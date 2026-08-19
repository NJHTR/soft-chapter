# 持续学习闭环设计

owner: main-dev-agent, created: 2026-08-14

## 1. 闭环周期（设计）

```
采集 ──► 脱敏去重 ──► 评分 ──► 候选集 ──► 异步训练 ──► 评测 ──► 门禁 ──► 灰度发布 ──► 回滚(异常时)
```

- **采集**：`ai_feedback` 记录用户显式反馈（like/dislike/copy/adopt/edited/retry/ignore/manual），幂等键 `trace_id+message_id+type`（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 2 节）；无 trace 的历史消息可离线补采（后续）。
- **脱敏去重**：PII 幂等脱敏（六类模式）+ `dedup_hash`（sha256，基于脱敏后内容）去重；冲突保留高分样本（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 4-5 节）。
- **评分**：质量分 0-100（长度 20 / 重复度 20 / 反馈加权 30 / 格式 20 / 拒绝回答率 10）；`< 60` 置 `excluded`。
- **候选集**：`ai_candidate_sample` 状态机 `pending → training → trained / excluded / failed`；只从 `pending` 且未过期的样本采样。
- **异步训练**：`ai_pipeline.py` 编排（第 4-8 节），与 HTTP 请求线程完全隔离。
- **评测**：固定 holdout + 规则指标（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 2-3 节）。
- **门禁**：指标达标 + 安全规则扫描 + 人工确认（或 `AI_RELEASE_AUTO=true` 显式配置）→ Champion/Challenger 切换。
- **灰度发布**：`1% → 5% → 25% → 100%`，每档观察 ≥ 24h；越界自动回滚（第 7 节）。
- **回滚**：切回上一版本权重，旧权重永不删除（宪法 P4/P6）。

执行频率（P0 默认，全部可配置）：候选集构建每日一次（低峰）；训练每周一次或候选集增量 ≥ 阈值（默认 200 条）触发；发布最多每周一次且需人工确认。

## 2. 高置信样本优先

- 采样排序：`quality_score` 降序 → 同分按反馈密度（≥2 条显式反馈优先）→ 同分按 `created_at` 新优先。
- 反馈加权只接受显式信号：like/copy/adopt（+）、dislike/retry（−）、edited（编辑后更长视为修正 +）、ignore（0）；隐式信号（停留时间等）最多 ±5 附加分且默认关闭（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 5 节）。
- 拒绝回答率双向约束：该答却拒答、不该拒答却答，均在评分与评测中惩罚。
- 每轮训练样本上限（P0：≤2000 条）；超限按分截取，防止低质数据淹没。
- 样本去重后再按 `source_type` 比例控制：`chat:summary = 7:3`（P0 可配），避免单来源主导。

采样伪代码（P0 语义，实现于 `ai_pipeline.py`）：

```
SELECT * FROM ai_candidate_sample
WHERE status='pending' AND expires_at > NOW() AND quality_score >= 60
  AND dedup_hash NOT IN (SELECT dedup_hash FROM ai_holdout_samples)   -- 防污染
ORDER BY quality_score DESC, feedback_density DESC, created_at DESC
LIMIT max_samples(2000)  -- 超限按分截取
-- 再按 source_type 比例 7:3 二次平衡, 并混入 replay buffer(≤500 条, 第3节)
```

## 3. Replay Buffer 防灾难性遗忘

- 保留历史高置信样本子集（P0：最近 3 轮训练的前 10% 高分样本，容量 ≤500 条）混入本轮训练。
- 来源：`ai_candidate_sample` 中 `status=trained` 且 `quality_score ≥ 80` 的记录；按 `sample_key` 回读 JSONL 缓存，不重复读库。
- 评测侧同时保留 replay 子集（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 2 节）：新模型在 replay 子集得分下降 ≥ 5 个百分点 → 门禁不通过（防遗忘检测）。
- replay 样本过期（`expires_at`）后自然退出，不强制保留。

## 4. 训练方法：LoRA/QLoRA

- P0 采用 LoRA（3B 基座，fp16，本机可跑）；必要时 QLoRA（4-bit NF4，`bitsandbytes` 已在 pyproject.toml 依赖，版本 0.44-0.46）。
- 默认超参（对齐现状 `finetune_summary.py` 已验证配置）：`lora_r=8`、`lora_alpha=16`、target modules `q_proj/k_proj/v_proj/o_proj`、`per_device_batch=1`、`grad_accum=4`、`max_length=512`、`lr=2e-4`、warmup 10 步；rank/序列长度选择依据 [LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 6 节。
- 数据格式：JSONL `{"keyword", "summary"}`（摘要域，与 `finetune_summary.py` 兼容）或 `{"user_msg", "assistant_msg"}`（对话域，AI-004 导出按 `source_type` 区分）。
- 基座：`Qwen/Qwen2.5-3B-Instruct`（本机缓存已有，离线可用）；8B 训练仅限 AutoDL 等远程环境（[LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 2 节）。

## 5. 训练预算 / 最大步数 / 早停 / checkpoint / 恢复

- **预算**：每次训练任务声明 `max_steps`（P0 默认 1500）、`max_runtime_sec`（P0 默认 8h）、`max_memory_gb`（P0 默认 ≤6GB 显存，Low Resource 第 8 节）；任一超限即终止并保留最后 checkpoint。
- **早停**：基于 holdout 子集 eval_loss，`patience=3`（每 100 步评估）；早停后保留最优 checkpoint。
- **Checkpoint**：按步保存（P0 每 200 步），目录 `checkpoint-<step>`（与现状 `distill_output/checkpoint-500/915` 风格一致）；保存 optimizer state 支持恢复。
- **恢复**：`--resume-from <checkpoint>` 显式指定；不带参数从零开始（现状 `finetune_summary.py` 无 `resume_from_checkpoint`，属新增能力）。
- **Dry-run**：`--dry-run` 只加载数据与模型、跑 1 个 batch 后退出，**不落任何 checkpoint**（AI-005 DoD 强制断言），用于验证数据/配置。
- 任务日志：每次训练写任务 ID、参数、数据指纹（sha256）、起点 checkpoint，供审计与恢复。
- 训练任务状态机：`queued → running → (completed | stopped | failed)`；`running` 内记录当前 step/checkpoint；早停/预算超限/外部中断统一落状态（第 6 节）。
- 训练观测指标（写入训练报告，不进 `metrics.json` 门禁）：step 耗时、GPU 小时、显存峰值、当前 eval_loss、数据指纹。
- 成本估算参考（3B LoRA，batch 1 + acc 4，max_steps 1500，max_length 512）：单步约 0.8-1.5s（RTX 4060 级别），单轮约 1-2 GPU 小时；8B QLoRA 约 6-10×，仅限远程环境（[LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 8-9 节）。数值为设计估算，落地后以实测为准。

## 6. 失败分类与恢复

| 失败类别 | 表现 | 恢复动作 |
|---|---|---|
| 数据失败 | JSONL 损坏、缺字段、0 样本 | 任务终止，样本状态回 `pending`，记录样本键范围，不重训 |
| 环境失败 | 显存 OOM、进程被杀、依赖缺失 | 任务标记 `failed`，checkpoint 保留；下次 `--resume-from` 恢复 |
| 中断 | 部署/关机中断训练 | 从上次 checkpoint 恢复；`expires_at` 未过期的候选集保留 |
| 评测失败 | holdout 指标缺失/未达标 | 不允许发布；Challenger 标记 failed，原因写入 `metrics.json` |
| 发布失败 | 灰度越界回滚 | 回滚至 Champion；保留 Challenger 权重待人工分析；不自动重试发布 |

- 重试策略：环境类失败允许自动重试 1 次（相同参数）；数据/评测类失败不允许自动重试。
- 训练终止的三类边界（预算超限 / 早停 / 外部中断）统一写任务状态：`completed` / `stopped` / `failed`。
- 恢复前置检查：`--resume-from` 前校验 checkpoint 完整性（config/optimizer/step 计数一致），数据指纹变化（候选集改动）时拒绝跨数据恢复——防止"同一 checkpoint 续到不同数据"的隐性污染。

## 7. 暂停 / 关闭开关与回滚语义

| 开关 | 生效语义 | 与训练任务的关系 |
|---|---|---|
| `AI_TRAINING_ENABLED=false` | 正在训练的任务在下一个 checkpoint 边界停止（不 kill 进行中的 step），记录 `stopped` | 恢复需显式 `--resume-from`；排队任务取消 |
| `AI_AUTO_LEARNING_ENABLED=false` | 停止候选集构建与训练调度 | 反馈采集仍继续（只落 `ai_feedback`，不转候选集） |
| `AI_MODEL_VERSION=<上一版本>` | Gateway 切换权重/provider，灰度流量 ≤5 分钟全量切回 | 不删除 Challenger/旧权重（宪法 P4） |

- 开关状态持久化于 `ai_ctrl`（配置表）或环境变量；变更写审计日志（[SECURITY.md](./SECURITY.md) 第 5 节）。

## 8. 明确约束：异步执行、不阻塞 HTTP 请求线程

- 训练、评测、候选集构建、门禁决策全部运行于独立进程/线程池（`ai_pipeline.py`、后台调度器），与 HTTP 请求线程零共享可变状态。
- HTTP 路径上唯一允许的 AI 计算是推理（gateway 调用），且受超时/取消约束（[ARCHITECTURE.md](./ARCHITECTURE.md) M2）。
- 候选集构建中的脱敏/去重/评分若与请求同进程，只能作为批量任务在空闲线程池运行，不得出现在 `chat` 请求处理路径。
- **验收口径（AI-005 DoD）**：训练任务运行中，`/api/ai/chat` p95 与无训练时对比 ≤ 基线 +5%（并发压测验证）。

## 9. 低峰调度与资源隔离

- 训练默认仅在低峰窗口运行（P0 配置 `AI_TRAIN_WINDOW=02:00-06:00` 或显式触发）。
- 训练进程与 LocalWorker 推理进程隔离：独立进程；可配置 CPU 配额、内存上限、显存上限（[LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 8 节）；训练期间推理优先。
- 并发约束：同时只允许 1 个训练任务；评测可并行（只读，内存受限）。

## 10. 与现状产物的衔接

- 现状 `distill/` 蒸馏流水线及产物（8B adapter、`distill_output/`、`training_data*.jsonl` 含 `_virtual` 样本）为**一次性离线产物**，可作为 AI-005 训练编排的参考与评测素材，但不作为持续学习闭环的在线数据路径；其 `config.py` 含密钥，须先按 [SECURITY.md](./SECURITY.md) 轮换与 env 迁移。
- 现有 `finetune_summary.py`（无 resume/dry-run）由 `ai_pipeline.py` 取代或包装：保持 JSONL 数据格式与 `--data/--epochs/--lora-r` 参数兼容。
- 本文档为设计；表/命令/开关均未实现，任务落地见 [AI-004-auto-dataset](../project-constitution/tasks/AI-004-auto-dataset.md)、[AI-005-evaluation-training](../project-constitution/tasks/AI-005-evaluation-training.md)、[AI-006-release-rollback](../project-constitution/tasks/AI-006-release-rollback.md)。

## 11. 一轮闭环示例（walkthrough，P0 验收脚本）

1. **D1**：用户对某回复点"赞" → `POST /api/ai/feedback`（trace_id/message_id 幂等）落 `ai_feedback`；
2. **D1 低峰**：候选集构建任务扫描新反馈 → 脱敏 → 去重 → 评分 → upsert `ai_candidate_sample`（`pending`）；
3. **D8（增量 ≥200 条触发或周任务）**：`ai_pipeline.py train` 采样（高置信优先 + replay ≤500）→ LoRA 训练（预算内）→ `completed` + 训练报告；
4. **D8**：`ai_pipeline.py eval --holdout v1 --model challenger-<date>` → `metrics.json` + 对 Champion 的 `compare_metrics.json`；
5. **D8 人工**：`release promote challenger-<date> --actor <operator>` → 灰度 1%；
6. **D9-D14**：逐档 5% → 25% → 100%（每档 ≥24h）；
7. **异常时（任意档）**：越界指标 → 自动 rollback → 版本切回 + 审计事件 + 24h 内人工复盘；
8. **全量后**：新版本记为 `champion-vN`，旧版本转"上一版本"保留（不删除）。

该 walkthrough 在 AI-005/AI-006 DoD 中逐步落为自动化验收用例（数据为合成/脱敏样本）。