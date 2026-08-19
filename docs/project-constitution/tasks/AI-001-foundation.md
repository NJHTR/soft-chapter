# AI-001：AI 宪法、架构、数据契约、评测发布、低资源优化、安全文档与密钥迁移

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`completed`
- 依赖：无
- 拥有：`docs/ai-agent/`（新建）；`server/src/main/resources/application.yml`（仅限密钥 env 化一处修改）
- 禁止修改：RTC 域（`server/.../rtc/`、`src/modules/rtc/`、`docs/contracts/`、`docs/architecture/`）、用户未提交工作区（`admin/`、live/engine/websocket/controller、`src/pages/live`、`src/utils/streaming`、`streaming-engine`、`docs/runtime`、`docs/verification`、`server/python/autodl_upload.zip`）

## 目标

- 建立 AI 持续学习闭环的全部设计文档与任务索引：宪法、架构、闭环设计、数据契约、评测发布、低资源优化、安全风险记录。
- 完成 `application.yml` 中真实密钥的 env 化迁移（DeepSeek/邮件/MinIO/JWT，另含 Redis），并把 Python 侧（`export_training_data.py`、`distill/config.py`）密钥轮换风险登记入 SECURITY 文档。
- 风险记录：已发现硬编码密钥清单精确到行号（见 [../../ai-agent/SECURITY.md](../../ai-agent/SECURITY.md)），文档本身可安全提交（掩码、不写完整值）。

## 非目标

- 不实现任何 AI 代码（runtime/gateway/feedback/训练/发布均属后续任务）。
- 不改动 `AIController`、`SearchSuggestionService` 的行为逻辑（仅允许 AI-001 之外的后续任务按各自 DoD 修改）。
- 不改动 RTC 域与用户未提交工作区（只读）。

## 事实基线（勘察结论，文档必须引用）

- `AIController` 直连 DeepSeek（无超时/重试/取消/流式/trace_id，占位回复分支存在）；`SearchSuggestionService` 以 ProcessBuilder 管理 `search_summary.py --serve`（stdin/stdout 协议，8B→3B 回退，无探活/自动重启）；3B 本机可跑，8B 无基座缓存离线不可跑；`distill/` 蒸馏产物齐全（adapter、checkpoint、metrics train_loss 0.550 / eval_loss 0.381、train/val 2932/764）。
- `application.yml` 硬编码密钥行号：L19 邮件、L39 JWT、L71 Redis、L83 DeepSeek、L90-91 MinIO；Python 侧 `export_training_data.py` L25-32、`distill/config.py` L8-15/L18-25；`autodl_upload.zip` 建议删除/重打包。
- 数据库迁移编号已用到 035，新迁移从 036 起（见 [../../ai-agent/DATA_CONTRACT.md](../../ai-agent/DATA_CONTRACT.md)）。
- `docs/verification/AI_PIPELINE.md` 为 C++ 视觉 stub 验证报告，与本文档集无关，不得混淆/宣称真实能力。

## Definition of Done

- [ ] `docs/ai-agent/` 六文档 + `docs/project-constitution/tasks/AI-000-index.md` 与 AI-001~AI-006 任务文件入库，交叉引用相对路径正确。
- [ ] `application.yml` 中 `deepseek.api-key`、`spring.mail.password`、`minio.access-key/secret-key`、`jwt.secret`（及 redis password）改为 `${ENV:...}` 形式（默认空或示例值），真实值不再出现在仓库可提交内容中。
- [ ] `application.example.yml` 更新为 env 形式并注释测试环境变量用法；README/启动文档补充环境变量清单（指向 [../../ai-agent/SECURITY.md](../../ai-agent/SECURITY.md) 第 3 节）。
- [ ] 仓库 `git grep` 无真实密钥模式（`sk-[A-Za-z0-9]{16,}` 等）；SECURITY.md 全掩码可安全提交。
- [ ] 密钥轮换计划（DeepSeek 两把、DB/Redis/MinIO 共用密码、邮箱授权码、JWT）已按 [../../ai-agent/SECURITY.md](../../ai-agent/SECURITY.md) 第 4 节登记，轮换动作在 AI-001 评审时确认执行。

## 交付物

- 六文档：AI_AGENT_CONSTITUTION / ARCHITECTURE / CONTINUOUS_LEARNING_DESIGN / DATA_CONTRACT / EVALUATION_AND_RELEASE / LOW_RESOURCE_OPTIMIZATION / SECURITY（`docs/ai-agent/`）。
- 任务索引 `docs/project-constitution/tasks/AI-000-index.md` 与 AI-001~AI-006 任务文件。