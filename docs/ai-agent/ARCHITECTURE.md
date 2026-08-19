# AI 模块架构

owner: main-dev-agent, created: 2026-08-14

## 1. 现状基线（勘察事实，非设计）

**链路 A — AI 客服对话**
- `server/src/main/java/com/douyin/controller/AIController.java` 提供 `GET /api/ai/history`（按 product_id 查 `t_ai_chat_message`）与 `POST /api/ai/chat`。
- `/api/ai/chat` 直接以 `RestTemplate` 调 DeepSeek `POST {base-url}/v1/chat/completions`：`model=deepseek-chat`、`max_tokens=500`、`temperature=0.7`；**无超时、无重试、无取消、无流式、无 trace_id**；返回结构 `{reply, role}`；回复与用户消息存 `t_ai_chat_message`（entity `com.douyin.entity.AiChatMessage`，mapper `com.douyin.mapper.AiChatMessageMapper`，表名实测 `t_ai_chat_message`）。
- 占位回复分支：`apiKey` 为 `"your-api-key-here"` 或空时返回固定占位文案（"客服功能正在配置中…"）。

**链路 B — AI 搜索摘要**
- `server/src/main/java/com/douyin/service/SearchSuggestionService.java` 用 `ProcessBuilder` 拉起 `server/python/search_summary.py --serve`（stdin/stdout 协议：输入 JSON 或纯文本；输出 `SUMMARY:...` + `__END__` / `ERROR:...`；启动输出 `READY`；`EXIT` 退出）。
- 启动顺序：先 `python -m distill.summary_service`（8B 蒸馏），失败自动回退 3B `search_summary.py`；Python 定位优先 `.venv/Scripts/python.exe`，其次 `python3/python/py`；设置 `HF_HUB_OFFLINE=1`、`PYTHONIOENCODING=utf-8`。
- **无健康探活、无自动重启**（daemon 意外退出仅在下一次请求时惰性拉起）；单线程顺序处理，无请求队列保护。

**Python 侧事实**
- `search_summary.py`：Qwen2.5-3B-Instruct（本机 HF 缓存已有）；`--serve` 只加载一次模型（常驻）；tokenizer 调用**无 `max_length` 输入截断**；生成 `max_new_tokens=512`、`temperature=0.7`、`top_p=0.92`、`repetition_penalty=1.15`；`ADAPTER_DIR=summary_lora_adapter`（目录不存在，即当前未加载微调适配器）；无推理缓存。
- `search_summary_8b.py`：Qwen3-8B-Instruct + `distill_lora_adapter`（adapter 存在，是蒸馏训练产物），但本机无 8B 基座缓存 → **离线跑不了**。
- `distill/`：完整蒸馏流水线；`config.py` 含 DB 明文密码与真实 DeepSeek key（风险见 [SECURITY.md](./SECURITY.md)）；`summary_service.py` 实现 LRU 256 推理缓存、Attention 探测 FA2 > SDPA > eager、4-bit 加载。
- 产物：`distill_lora_adapter/`（8B adapter）、`distill_output/`（checkpoint-500/915、merged_model、`train_metrics.json`：train_loss 0.550 / eval_loss 0.381 / epoch≈4.98）、`training_data.jsonl`（5029 行）、`training_data_cleaned_train/val.jsonl`（2932/764，含 `_virtual` 合成样本）。
- `pyproject.toml`（uv）：torch 2.4-2.7（cu124）、transformers 4.46-4.55、peft、accelerate、bitsandbytes、sentence-transformers 等；**无 vllm/llama.cpp**。`.venv` 存在（py3.12，torch 2.6.0+cu124）。

## 2. 现状-目标映射

| 现状（事实） | 目标（设计） | 对应模块/任务 |
|---|---|---|
| `AIController.chat` 直连 DeepSeek，无超时/重试/取消/流式/trace_id | 请求先入 Agent Runtime（trace_id/超时/预算/幂等），再经 Model Gateway 路由 | M1、M2；AI-002 |
| 占位回复分支（apiKey 空时） | 保留为"provider 全不可用"的最终兜底，语义改为可观测（记录错误指标），默认 provider 仍是 DeepSeek | M2；AI-002 |
| `SearchSuggestionService` 手管 ProcessBuilder + stdin/stdout 协议，无探活/自动重启 | LocalWorker 管理器统一接管（复用同协议），健康探活 + 异常自动重启 + 请求队列 | M2（LocalWorker）；AI-002 |
| 8B 蒸馏本机离线不可跑、3B 本机可跑 | LocalWorker 支持多版本 worker 并存按配置路由；8B 仅 AutoDL/远程 GPU 或本机缓存补齐后启用 | M2；AI-002、Low Resource |
| 消息落 `t_ai_chat_message`，无反馈采集 | 采集落 `ai_feedback`，构造候选集 `ai_candidate_sample` | M4；AI-003/AI-004 |
| 无 trace_id、无质量数据 | 全链路 trace_id；样本带来源/时间/授权/质量分/模型版本 | M1（引入）、M4（落表） |
| 训练为一次性 CLI（`finetune_summary.py`/`distill/`），无预算/断点恢复/dry-run | `server/python/ai_pipeline.py` 编排：预算/早停/checkpoint/resume/dry-run | M5；AI-005 |
| 无评测集、无版本对比、无灰度 | 固定 holdout 评测 + Champion/Challenger + 灰度/自动回滚 | M6；AI-005/AI-006 |

## 3. 六大模块

### M1 Agent Runtime（`com.douyin.ai.runtime`）⭐P0
职责：对话请求编排——意图识别、工具调用（P0 仅商品查询类只读工具）、上下文预算（token 截断/窗口化）、超时/重试/取消、`trace_id` 生成与透传、幂等键、权限校验（用户/租户维度）。

接口草案（Java，设计）：
- `ChatReply chat(ChatRequest req)`；`ChatRequest{userId, productId, message, history, productInfo, traceId?, idempotencyKey?}`
- `ChatReply` 兼容现状 `{reply, role}` 结构（AI-002 DoD：响应结构不变）。
- 内部产出 `TraceContext{traceId, modelVersion, provider, latencyMs, tokenUsage}`，随响应头与指标输出。
- 幂等：`idempotencyKey` 存在时，重复请求返回首次结果（P0 按 key 缓存，TTL 10 分钟）。

P0 范围：trace_id、超时（默认 15s）/重试（≤2 次）/取消传播、幂等键、上下文预算（截断+最近 N 轮窗口）、权限校验。
后续：工具调用扩展、流式对接前端、多租户策略细化。

### M2 Model Gateway（`com.douyin.ai.gateway` + `com.douyin.ai.provider`）⭐P0
职责：屏蔽 provider 差异；提供健康检查、流式输出（SSE）、fallback 链、p95 延迟与显存/内存监控。

Provider 抽象（OpenAI-compatible）：
- `AIProvider` 接口：`ChatCompletion complete(ChatRequest, TraceContext)`、`Stream<ChatChunk> stream(...)`、`Health health()`、`ProviderStats stats()`。
- `DeepSeekRemoteProvider`（远端，默认）：`POST {base-url}/v1/chat/completions`，复用现状 base-url 配置（env 化后为 `${DEEPSEEK_BASE_URL}`）；实现连接/读超时（默认 15s 可配）、指数退避重试（≤2 次）、取消传播（调用方取消 → 中断上游）。
- `LocalWorkerProvider`（本地常驻）：复用 `search_summary.py --serve` stdin/stdout 协议（READY / `SUMMARY:`+`__END__` / `ERROR:` / EXIT）；由 `LocalWorkerManager` 管理进程生命周期：启动 → 等待 READY（超时默认 120s）→ 探活（心跳/超时判定，默认 60s）→ 异常自动重启 → 优雅退出（EXIT）。单线程模型 ⇒ 请求在 Gateway 侧串行化 + 队列（队列上限可配，溢出走 fallback）。
- 其他 OpenAI-compatible 远端 provider 由接口扩展（后续）。

Gateway 行为：provider 链按配置排序，失败顺序 fallback；全失败 → 占位回复 + 错误指标（保留现状占位语义但可观测）；支持流式输出（SSE）为 P0 目标能力（现状无流式，属新增）。

监控（P0）：每 provider 记录 p95/p99 延迟、错误率、请求数、token 用量；本地 worker 记录内存 RSS、显存（`torch.cuda.memory_allocated`）；只进监控系统，不落业务日志明文。

### M3 Memory-RAG（`com.douyin.ai.memory`）⭐P0（记忆基础）/ 后续（RAG）
职责：会话历史（`t_ai_chat_message` 读写，保持现状兼容）、用户授权长期记忆、会话摘要、知识检索。

接口草案：`List<MemoryEntry> load(userId, productId, window)`；`save(userId, productId, role, content, traceId)`；`SummaryResult summarize(session)`（后续）。

P0 范围：历史读写接入 Runtime（替代现状 controller 内直查逻辑）、上下文预算裁剪（截断+窗口）。
后续：用户授权长期记忆（AI-003 摘要待办）、会话摘要、向量检索。

### M4 Feedback and Data Quality（`com.douyin.ai.feedback` + `com.douyin.ai.dataquality`）⭐P0
职责：反馈采集（like/dislike/copy/adopt/edited/retry/ignore/manual）幂等入库 `ai_feedback`；构造候选集：脱敏（幂等）→ 去重（sha256）→ 质量评分（0-100）→ 幂等 upsert `ai_candidate_sample`。

接口草案：`recordFeedback(FeedbackEvent)`；`buildCandidates(window)`（批量任务）；`CandidateSample upsertCandidate(Sample)`；`String export(sampleKeys, sourceType)`。

P0 范围：采集 API（`POST /api/ai/feedback`）、用户数据删除（`DELETE /api/ai/user-data`）、脱敏/去重/评分管线、候选集导出 JSONL。
表结构见 [DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 2-3 节；任务 AI-003/AI-004。

### M5 Continuous Learning（`server/python/ai_pipeline.py`）⭐P0（编排）/ 后续（自动调度）
职责：异步训练编排——候选集采样（高置信优先 + replay buffer）、LoRA/QLoRA 训练（预算/早停/checkpoint/resume/dry-run）、训练进程资源隔离、失败分类与恢复。

约束：绝不阻塞 HTTP 请求线程；训练与推理进程隔离（[LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 8 节）。

接口草案（CLI）：`ai_pipeline.py train --candidates <jsonl> --base-model qwen2.5-3b-instruct --lora-r 8 --dry-run --budget ... --resume-from <ckpt>`；`ai_pipeline.py eval --holdout v1 --model <version>`。

细节见 [CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md)；任务 AI-005。

### M6 Evaluation and Release（`com.douyin.ai.eval` + `com.douyin.ai.release`）⭐P0
职责：固定 holdout 评测（命令化、版本化、防污染）、指标计算、Champion/Challenger 对比、灰度发布、自动回滚、运营开关（暂停训练/关闭自动学习/回滚上一模型）。

接口草案：`eval --model <version> --holdout v1` → `metrics.json`；`release promote <challenger> --actor <operator>`；`release rollback --reason <metric>`；`ai_ctrl` 读写开关。

细节见 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md)；任务 AI-005（评测）、AI-006（发布）。

## 4. 数据流图（设计）

```
 前端 ── POST /api/ai/chat ──► ┌──────────────────────┐
                               │ M1 Agent Runtime      │ trace_id/预算/权限/幂等
                               └───────────┬──────────┘
                                           │ ChatRequest + TraceContext
                              ┌────────────▼────────────┐
                              │ M2 Model Gateway         │ 健康检查/流式/p95/显存
                              │ ┌──────────────────────┐ │
                              │ │ provider 链 (fallback)│ │
                              │ └──┬──────┬───────┬────┘ │
                              └────│──────│───────│──────┘
                                   │      │       │
                     DeepSeek 远端 │      │       │ 其他 OpenAI-compatible (后续)
                     (默认)        │ LocalWorker(本地常驻,复用 search_summary 协议)
                                   │
                              ┌────▼───────────────────────────┐
                              │ M3 Memory-RAG                  │ 读/写 t_ai_chat_message
                              └────┬───────────────────────────┘
                                   │
                              ┌────▼───────────────────────────┐
                              │ M4 Feedback & Data Quality     │
                              │ ai_feedback ──脱敏/去重/评分──► │ ──► ai_candidate_sample
                              └────┬───────────────────────────┘
                                   │ 异步导出 JSONL
                              ┌────▼───────────────────────────┐
                              │ M5 Continuous Learning         │ ai_pipeline.py
                              │ 预算/早停/checkpoint/resume    │ ──► LoRA checkpoint
                              └────┬───────────────────────────┘
                                   │
                              ┌────▼───────────────────────────┐
                              │ M6 Evaluation & Release        │
                              │ holdout 评测 → Champion/Challenger ──► 灰度/回滚 → Gateway
                              └────────────────────────────────┘
```

## 5. 非功能目标（P0 设计值）

| 项 | 目标 |
|---|---|
| 延迟 | DeepSeek 路径 p95 ≤ 5s；本地 worker p95 ≤ 15s（3B fp16，无缓存冷启动除外） |
| 可用性 | provider 全挂时占位回复可用（现语义），错误率可观测 |
| 资源 | 本地 worker 单实例 RSS/显存上限可配（默认 6GB）；训练进程隔离（Low Resource 第 8 节） |
| 可观测 | 全链路 trace_id；每请求指标（provider/延迟/token）；monitoring 面板为后续项 |

## 6. 模块依赖与任务归属

| 模块 | 目录 | P0 任务 | 后续任务（AI-00X） |
|---|---|---|---|
| M1 Agent Runtime | `com.douyin.ai.runtime` | AI-002 | AI-002 扩展 |
| M2 Model Gateway | `com.douyin.ai.gateway`、`com.douyin.ai.provider` | AI-002 | AI-002 扩展 |
| M3 Memory-RAG | `com.douyin.ai.memory` | AI-003（历史/预算） | 长期记忆、摘要、检索 |
| M4 Feedback & DataQuality | `com.douyin.ai.feedback`、`com.douyin.ai.dataquality` | AI-003（feedback）、AI-004（候选集） | 自动扩样 |
| M5 Continuous Learning | `server/python/ai_pipeline.py` | AI-005 | 自动调度/增量训练 |
| M6 Evaluation & Release | `com.douyin.ai.eval`、`com.douyin.ai.release` | AI-005（评测）、AI-006（发布） | 多指标门禁 |

## 7. 非目标（不承诺）

- 不实现在线学习/强化学习/自训练（宪法 P1-P3 禁止）。
- 不实现 vllm/llama.cpp 服务化推理（pyproject 现状无这些依赖；引入属后续决策并需验证）。
- 8B 模型不在本机默认启用（无基座缓存，[LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 2 节）。
- 本文档为设计，代码尚未实现；"目标"列均为待实现语义，不得以 [docs/verification/AI_PIPELINE.md](../verification/AI_PIPELINE.md)（C++ 视觉 stub 报告）冒充本链路能力。