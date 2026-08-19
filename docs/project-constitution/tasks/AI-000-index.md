# AI 任务索引

owner: main-dev-agent, created: 2026-08-14

状态含义：`planned` 计划中，`in_progress` 进行中，`review` 审查中，`verified` 已验证，`completed` 完成，`blocked` 外部阻断。

| ID | 任务 | 状态 | 依赖 | 主要交付物 |
|---|---|---|---|---|
| AI-001 | AI 宪法、架构、数据契约、评测发布、低资源优化、安全文档；密钥 env 迁移与风险记录 | `completed` | 无 | `docs/ai-agent/` 七文档、`application.yml` 密钥 env 化（唯一允许修改）、SECURITY 风险清单 |
| AI-002 | Agent Runtime 与 Model Gateway（本地 worker + DeepSeek fallback + 流式 + 健康检查） | `planned` | AI-001 | `com.douyin.ai.runtime/gateway/provider`、兼容 `/api/ai/chat`、trace_id |
| AI-003 | 反馈数据模型与采集 API、长期记忆与会话摘要 | `planned` | AI-001 | `migration_036`（ai_feedback）、采集 API、用户数据删除接口 |
| AI-004 | 自动候选集（脱敏/去重/筛选/打分） | `planned` | AI-003 | `migration_037`（ai_candidate_sample）、`com.douyin.ai.dataquality`、JSONL 导出 |
| AI-005 | 离线评测（固定 holdout）+ dry-run + LoRA 训练编排 | `planned` | AI-004 | `server/python/ai_pipeline.py`、`com.douyin.ai.eval`、`metrics.json` |
| AI-006 | Champion/Challenger、灰度发布、自动回滚、运营开关 | `planned` | AI-005 | `com.douyin.ai.release`、`ai_ctrl` 开关与版本链 |

## 与 RTC 任务族的关系

- AI 是**独立任务族**：不与 RTC-001~RTC-010 共享任务编号，不依赖 RTC 任务的完成状态。
- 目录边界：AI 拥有 `com.douyin.ai.*`、`server/python/`、`docs/ai-agent/`；RTC 拥有 `server/.../rtc/`、`src/modules/rtc/`、`docs/contracts/`、`docs/architecture/`——**拥有目录不重叠**。
- 资源共用：AI-002 起的运行时（Java 服务、GPU/内存、低峰窗口）与 RTC 服务共用同一服务器，但不改动 RTC 域文件；资源隔离与训练窗口见 [../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md](../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md) 第 8-9 节。
- 只读边界：用户未提交工作区（live/engine/websocket/controller、`src/pages/live`、`src/utils/streaming`、`streaming-engine`、`docs/runtime`、`docs/verification`、`server/python/autodl_upload.zip`）对 AI 任务族只读；`docs/verification/AI_PIPELINE.md`（C++ 视觉 stub 报告）与 AI 学习闭环无关，AI 任务不得以其宣称真实能力。

## 波次说明

- 波次 A（文档与安全）：AI-001 完成后，密钥风险消除、设计基线冻结。
- 波次 B（运行时与数据）：AI-002 / AI-003 可并行启动（AI-003 仅依赖 AI-001；trace 联调依赖 AI-002，任务内已兼容可空）。
- 波次 C（数据→训练→发布）：AI-004 → AI-005 → AI-006 严格顺序，每步 DoD 通过才允许下一步。

## 当前执行指针（2026-08-14）

- 当前任务：AI-001（全部任务 `planned`，文档起草完成、待主智能体审阅入库）。
- 下一任务候选：AI-002、AI-003（波次 B，可在 AI-001 审阅通过后并行开始）。