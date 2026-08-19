# 低资源优化（LOW_RESOURCE_OPTIMIZATION）

owner: main-dev-agent, created: 2026-08-14

## 1. 现状对应项（勘察事实）

| 现状项 | 事实 | 缺口（本设计要补） |
|---|---|---|
| 3B 模型 | `search_summary.py`：Qwen2.5-3B-Instruct，本机 HF 缓存已有；`--serve` 常驻只加载一次模型 | 无输入截断（tokenizer 无 max_length）；无推理缓存（仅 `distill/summary_service.py` 有 LRU 256）；无健康探活/自动重启；无请求队列保护 |
| 8B 模型 | `search_summary_8b.py` + `distill_lora_adapter`（adapter 存在），本机无 Qwen3-8B 基座缓存 | 离线跑不了；仅 AutoDL 或补缓存后启用 |
| 蒸馏推理 | `distill/summary_service.py`：4-bit NF4 加载、LRU 256 推理缓存、Attention 探测 FA2 > SDPA > eager、加载后打印显存 | 未接 Java 侧探活；仍无输入截断 |
| 训练脚本 | `finetune_summary.py`：LoRA r=8/alpha=16/q,k,v,o_proj、batch 1、grad_accum 4、max_length 512、fp16、lr 2e-4 | 无 resume/dry-run/预算/早停/checkpoint 策略 |
| 依赖 | pyproject.toml：torch 2.4-2.7（cu124）、transformers 4.46-4.55、peft、accelerate、bitsandbytes、sentence-transformers；无 vllm/llama.cpp；.venv 为 py3.12 + torch 2.6.0+cu124 | 量化工具链仅 bitsandbytes；无 GGUF/GPTQ/AWQ |

## 2. 模型选型原则

- **1.5B-3B 优先**；P0 训练/推理基座默认 `Qwen/Qwen2.5-3B-Instruct`（本机缓存可用、离线可跑）。8B 不默认训、不默认部署（基座缓存缺失 + 显存与延迟成本），仅作为 AutoDL 等远程 GPU 环境的 Challenger 候选（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 4 节）。
- 每次选型记录：基座、量化、上下文窗口、显存占用、p95 延迟、训练 GPU 小时，写入任务日志（不写业务日志明文，[SECURITY.md](./SECURITY.md) 第 5 节）。

## 3. 量化与加载

- P0 推理：fp16 优先；OOM 回退 4-bit NF4（bitsandbytes）——现状 `search_summary.py` 已实现该回退路径（`load_in_4bit + nf4 + fp16 compute`），可复用。
- 备选（后续，需新增依赖 + 延迟/质量对比验证）：4-bit GGUF（llama.cpp，当前无依赖）、GPTQ/AWQ（需校准数据）；任何新依赖先在 `.venv`（py3.12）验证再入 pyproject.toml。
- 常驻进程约束：**禁止每次请求重载模型**；`--serve` 常驻已满足，新 LocalWorker 同样要求 READY 一次性加载（[ARCHITECTURE.md](./ARCHITECTURE.md) M2）。
- 显存记录：加载后、推理后分别记录 `torch.cuda.memory_allocated()`（`summary_service.py` 已有加载后打印，扩展为周期采集）。
- 显存/延迟预算参考（设计估算，落地后实测校准；RTX 4060 8GB 级别）：
  | 配置 | 显存（推理常驻） | p95 延迟（512 tok 输出） | 适用 |
  |---|---|---|---|
  | 3B fp16 | ~6GB | 5-10s | 本机 P0 默认 |
  | 3B 4-bit NF4 | ~3GB | 8-15s | 与训练共存场景 |
  | 8B 4-bit NF4 | ~6GB+ | 15-30s | 仅远程/补缓存后（本机不可跑） |
  | 8B LoRA 训练（QLoRA） | ~8GB 峰值 | — | 仅 AutoDL |

## 4. 上下文管理

- **输入截断（P0，现状缺失）**：tokenizer 调用带 `max_length`（P0：摘要域 4096、对话域 2048），防止超长输入 OOM/慢推理；生成参数保持现状值（`max_new_tokens=512`、`temperature=0.7`、`top_p=0.92`、`repetition_penalty=1.15`）。
- **上下文预算公式（P0）**：`总预算 = system + 窗口历史 + 当前输入 ≤ max_length - max_new_tokens`；预算超限时按"最旧消息先裁剪"，仍超限再降级检索/摘要（后续）；预算数值与实测 token 数记录到监控，防止静默超限。
- **KV cache 与窗口**：不使用无限拼接；上下文按最近 N 轮窗口（P0：对话 12 轮）截取，早于窗口的内容丢弃或摘要化。
- **历史摘要（后续）**：超窗历史用摘要模型压缩（复用现有摘要模型），替代无限长 prompt。
- **检索替代（后续）**：Memory-RAG 只注入相关片段（top-k，P0 建议 k≤8），禁止把整段会话/全量视频列表塞进 prompt（防 OOM 与成本失控）。

## 5. 推理侧低资源要点

- 单线程顺序处理（现状 `--serve` 行为）：Gateway 侧串行化 + 请求队列（上限可配，默认 8；溢出走 fallback），避免并发进模型。
- 推理缓存（P0）：`summary` 域按 `sha256(keyword + canonical(context))` 做 LRU（容量可配，P0 256，对齐 `summary_service.py` 现有实现并抽为公共模块）。
- 健康探活（P0）：定期心跳（默认 60s 无响应即判 dead），异常自动重启 worker（现状无探活/无自动重启，属新增，AI-002）。
- 显存碎片：出现 ERROR 后执行 `torch.cuda.empty_cache()`（`summary_service.py` 已有），保持。

## 6. 训练侧参数策略

- LoRA：P0 `rank=4/8`、`alpha=2×rank`（现状默认 r=8/alpha=16 一致）；target modules 先 `q,k,v,o_proj`（现状一致）；**不默认**扩大到 `gate/up/down`（`distill/config.py` 全模块是远程 8B 两阶段场景，不适用本机 P0）。
- 序列长度：`max_length=512` 起步（现状一致）；加长前先用样本长度分布验证（P95 长度 < max_length 才允许调）。
- 梯度策略：`batch=1 + grad_accum=4`（现状一致）；gradient checkpointing 作为 QLoRA 场景开关（`distill/config.py` 已用，本机 LoRA 场景默认关）。
- 样本上限：每轮 ≤2000（[CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 2 节）；预算/早停/checkpoint/resume 见该文档第 5 节。
- 空闲调度：训练默认低峰窗口（该文档第 9 节：`AI_TRAIN_WINDOW=02:00-06:00` 或显式触发），避免与推理抢显存。
- 训练成本对照（供预算决策，设计估算）：3B LoRA fp16 单轮（1500 步 × 2000 样本）≈ 1-2 GPU 小时、峰值 ≤6GB；8B QLoRA ≈ 10-15 GPU 小时（远程）。"先 prompt/RAG 实验（小时级），后微调（天级）"的次序是资源纪律的一部分（第 7 节）。

## 7. 先验证再微调

- 训练前必须先做低成本实验：RAG/检索 + prompt/schema 优化（不涉及权重）在固定 holdout 上的指标；只有"不动权重达不到、且收益可量化"才进入微调（宪法 P1 闭环入口，门禁见 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 3 节）。
- 实验纪律：每次实验记录真实延迟与成本（推理 p95、训练 step 耗时与 GPU 小时、显存峰值），`metrics.json` 统一落盘（AI-005）；无记录的实验结果不参与决策。

## 8. 进程隔离与资源限制（P0 设计）

- 训练与推理必须分开进程：LocalWorker（推理常驻）与 `ai_pipeline.py`（训练）独立启动，互不共享 CUDA context；训练进程不 serve 请求。
- 资源上限（可配置）：
  - 训练进程：`max_memory_gb`（P0 ≤6GB 显存）、CPU 配额、并发训练任务 =1；
  - 推理 worker：内存 RSS 上限 + 重启阈值、显存阈值（OOM 即重启）。
- 本机共存策略：3B LoRA（fp16/QLoRA）训练必须与推理 worker 共存不 OOM（预算含两者峰值）；8B 训练仅在 AutoDL（无共存要求）。

## 9. 延迟与成本记录（P0）

- 推理：每请求记录 token 数、生成耗时、总耗时（聚合 p95）；`summary_service.py` 已有单次耗时日志（`推理完成: %dms`），扩展为指标输出与 `metrics.json`。
- 训练：每 step 耗时、总 GPU 小时、数据量；写回 `metrics.json`。
- 记录脱敏：不落业务日志明文（含密钥模式，[SECURITY.md](./SECURITY.md) 第 5 节）。

## 10. 依赖与工具链（现状约束）

- 当前无 vllm/llama.cpp：不假设存在服务化框架；P0 沿用 transformers `generate` + 常驻进程 stdin/stdout 协议。
- GGUF/GPTQ/AWQ 属后续评估项；新增依赖必须过 `.venv`（py3.12）兼容验证与延迟/质量对比，再更新 pyproject.toml。
- 现状蒸馏产物（8B adapter、`distill_output/`）不占本机常驻资源（离线不可跑），仅作为远程环境候选。

## 11. 决策清单（P0 落地时勾选）

1. 基座为 3B 本机缓存版，不引入 8B；
2. 推理截断 + LRU 缓存 + 探活/重启 + 请求队列全部启用；
3. LoRA r=8/alpha=16/q,k,v,o、512 窗口、batch 1 + acc 4；
4. 训练低峰窗口与资源上限配置生效；
5. 训练前保存 prompt/RAG 基线评测，证明微调必要性（第 7 节）。

## 12. 风险与回退

- 量化回退链：fp16 → 4-bit NF4 → CPU（仅演示，不用于线上）；任一步失败记录并告警，不静默降级质量。
- 上下文裁剪导致的事实性下降：若裁剪后事实性指标 < 90%（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 阈值）→ 要么增大窗口（验证显存）要么走检索（后续），不得牺牲指标换吞吐。
- 缓存失效风险：LRU 缓存只在 `summary` 域启用；对话域不缓存（上下文强相关），避免陈旧回复。

## 13. 监控与告警（P0）

| 指标 | 采集点 | 告警阈值（可配） |
|---|---|---|
| worker 存活/READY | LocalWorkerManager 探活 | 连续 3 次探活失败 |
| 推理 p95 | Gateway 每 provider | > 15s（3B 本地）持续 10 分钟 |
| worker 内存 RSS | LocalWorkerManager 周期采样 | > 上限（默认 6GB） |
| 显存占用 | Python 侧 `memory_allocated` 周期上报 | > 预算 80% |
| 训练 OOM/失败 | `ai_pipeline.py` 任务状态 | 任一 `failed` |
| 队列溢出 fallback 次数 | Gateway | 每分钟 fallback > 0 告警 |

告警只进监控/运维渠道，不写业务日志明文（[SECURITY.md](./SECURITY.md) 第 5 节）。

## 14. 8B 蒸馏产物启用标准（决策门）

仅当同时满足：① AutoDL/远程 GPU 环境可用或本机 8B 基座缓存补齐；② 在 holdout 上事实性/任务成功率 ≥ 3B Champion 且延迟/显存达标；③ 通过 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 全部门禁——才允许把 8B 蒸馏（`distill_lora_adapter`）作为 Challenger 引入。现状（本机无 8B 缓存 + 无远程验证）下该决策门不通过，3B 为 P0 默认（第 2 节）。