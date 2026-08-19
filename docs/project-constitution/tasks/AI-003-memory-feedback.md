# AI-003：反馈数据模型与采集 API、长期记忆与会话摘要

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`planned`
- 依赖：AI-001
- 拥有：`com.douyin.ai.feedback`（新建包）；迁移文件 `server/sql/migration_036_ai_feedback.sql`
- 禁止修改：RTC 域、用户未提交工作区；`t_ai_chat_message` 仅作为只读数据源

## 目标

- 建 `ai_feedback` 表（DDL 见 [../../ai-agent/DATA_CONTRACT.md](../../ai-agent/DATA_CONTRACT.md) 第 2 节，AI-003 拥有 `migration_036`）。
- 采集 API：`POST /api/ai/feedback`，幂等采集（`trace_id+message_id+feedback_type` 唯一），PII 脱敏入库（幂等规则）。
- 用户数据删除接口（`DELETE /api/ai/user-data` 语义：删 feedback、候选样本置 excluded 并匿名化）。
- 长期记忆与会话摘要：用户授权长期记忆 + 会话摘要（摘要为 P0 可留后续，见 DoD 标注）。

## 非目标

- 不构造候选训练集 / 不评分（AI-004）。
- 不实现训练与发布（AI-005/AI-006）。
- 不引入向量检索（后续 Memory-RAG 扩展，见 [../../ai-agent/ARCHITECTURE.md](../../ai-agent/ARCHITECTURE.md) M3 后续项）。

## 事实基线

- 现状无 feedback 表；`t_ai_chat_message`（`com.douyin.entity.AiChatMessage`，表名实测 `t_ai_chat_message`）保存角色/内容，可作为历史溯源来源。
- trace_id 由 AI-002 引入；本任务建表不阻塞，但采集 API 联调依赖 trace 字段可空/兼容（历史消息 message_id 兜底）。
- 迁移编号现状用到 035，`migration_036` 为下一个可用编号。

## Definition of Done

- [ ] `migration_036` 落地 `ai_feedback`（含 `uk_trace_msg_type` 幂等唯一键、`idx_user/product/created`）。
- [ ] `registerFeedback` 单测：幂等（同 key 重复提交不重复计）、枚举校验、`edited_content` 超长截断、脱敏入库。
- [ ] 脱敏单测覆盖 [../../ai-agent/DATA_CONTRACT.md](../../ai-agent/DATA_CONTRACT.md) 第 4 节六类模式（手机/身份证/邮箱/IP/银行卡/密钥）且幂等。
- [ ] `DELETE /api/ai/user-data`：删除用户 feedback 行、候选样本置 `excluded` 且 `user_id` 置 NULL（若对应行已存在）；审计记录删除事件。
- [ ] 长期记忆接口（load/save，授权标记）P0 实现；会话摘要标 ⭐P0 可留后续（任务内记录待办，不阻塞本任务完成）。

## 交付物

- `com.douyin.ai.feedback`（FeedbackEvent/FeedbackService/幂等唯一键、脱敏工具）
- `migration_036_ai_feedback.sql`、采集 API、用户数据删除 API
- 长期记忆模块（`com.douyin.ai.memory` 内 load/save + 授权标记）；会话摘要待办项