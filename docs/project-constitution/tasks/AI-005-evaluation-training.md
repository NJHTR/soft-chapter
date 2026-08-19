# AI-005：离线评测与训练编排（eval + dry-run + LoRA）

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`completed`
- 依赖：AI-004（候选集 JSONL 可用）
- 拥有：`server/python/ai_pipeline.py`（新建）、`com.douyin.ai.eval`（新建包，holdout 版本与指标计算）
- 禁止修改：RTC 域、用户未提交工作区；现状 `finetune_summary.py`/`distill/` 不修改（由 `ai_pipeline.py` 包装/取代，保持 JSONL 与参数兼容）

## 目标

- 离线评测命令：固定 holdout（`seed=42`、内容清单版本化、构建脚本入库 `ai_holdout`），指标见 [../../ai-agent/EVALUATION_AND_RELEASE.md](../../ai-agent/EVALUATION_AND_RELEASE.md) 第 2-3 节，输出 `metrics.json` + 对 Champion 的 `compare_metrics.json`。
- dry-run 训练命令：验证数据/配置后退出，**不落任何 checkpoint**。
- LoRA 训练编排：预算（max_steps/max_runtime_sec/max_memory_gb）、早停、checkpoint、resume、样本上限、replay buffer 混入（设计见 [../../ai-agent/CONTINUOUS_LEARNING_DESIGN.md](../../ai-agent/CONTINUOUS_LEARNING_DESIGN.md) 第 4-5 节）。
- 发布人工确认（P0 默认 `AI_RELEASE_AUTO=false`）与训练不阻塞 HTTP 的验证口径（同文档第 8 节）。

## 非目标

- 不实现灰度发布/回滚/开关（AI-006）。
- 不默认训练 8B（无基座缓存；仅 AutoDL 远程场景，见 [../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md](../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md) 第 2 节）。
- 不引入 vllm/llama.cpp 服务化（现状依赖无此项）。

## 事实基线

- 现状 `finetune_summary.py`：LoRA r=8/alpha=16/target q,k,v,o_proj、batch 1、grad_accum 4、max_length 512、fp16、lr 2e-4，无 resume/dry-run/预算/早停——本任务全部为新增能力。
- 现状评测仅 `distill_output/train_metrics.json`（train_loss 0.550 / eval_loss 0.381 / epoch≈4.98），不是发布依据；holdout 体系为新建。
- `.venv`（py3.12 + torch 2.6.0+cu124）为训练运行环境；训练/推理进程隔离（Low Resource 第 8 节）。

## Definition of Done

- [ ] 评测集固定性测试：同一命令两次运行输出一致（内容清单 + seed 固定 + 数据指纹）。
- [ ] `eval` 命令可对任一模型版本（champion/challenger）输出 `metrics.json`（含版本、指标、阈值、指纹、时间），并与 Champion 对比表。
- [ ] `--dry-run` 不落任何 checkpoint（测试断言目标目录无新增 checkpoint）。
- [ ] 训练编排：预算/早停/checkpoint 每 200 步/resume 显式指定；replay 混入与样本上限生效。
- [ ] 训练不阻塞 HTTP：并发压测（训练运行中 `/api/ai/chat` p95 ≤ 基线 +5%）。
- [ ] 发布人工确认路径就绪（默认人工确认；`AI_RELEASE_AUTO=true` 需显式配置并留审计）。

## 交付物

- `server/python/ai_pipeline.py`（eval / train / dry-run / resume 子命令）
- `com.douyin.ai.eval`（holdout 版本管理、指标计算、`metrics.json` 输出）
- `ai_holdout` 版本记录（内容清单 + seed + 构建脚本）与评测产物目录 `server/python/artifacts/`