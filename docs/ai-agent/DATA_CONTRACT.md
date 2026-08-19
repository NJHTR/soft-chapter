# 数据契约：反馈、候选样本、脱敏、去重、评分

owner: main-dev-agent, created: 2026-08-14

## 1. 范围与角色

本契约定义持续学习闭环的两张核心表（`ai_feedback`、`ai_candidate_sample`）与数据治理规则。现状相关表 `t_ai_chat_message`（`com.douyin.entity.AiChatMessage`，表名实测 `t_ai_chat_message`）为只读数据源：只允许被采集链路读取，不修改其结构。迁移编号：`ai_feedback` 为 `migration_036`（AI-003 拥有），`ai_candidate_sample` 为 `migration_037`（AI-004 拥有）；现状编号已用到 035（`migration_035_rtc_webhook_ledger`）。

## 2. ai_feedback（反馈采集）⭐P0

```sql
CREATE TABLE ai_feedback (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    product_id      BIGINT       DEFAULT NULL COMMENT '商品ID(非商品场景可空)',
    trace_id        VARCHAR(64)  NOT NULL COMMENT '请求链路ID(gateway 生成),幂等键组成部分',
    message_id      BIGINT       DEFAULT NULL COMMENT '关联 t_ai_chat_message.id',
    feedback_type   ENUM('like','dislike','copy','adopt','edited','retry','ignore','manual')
                                 NOT NULL COMMENT '反馈类型',
    rating          TINYINT      DEFAULT NULL COMMENT '评分 1-5(manual 类型填写)',
    edited_content  TEXT         DEFAULT NULL COMMENT '用户编辑后的内容(edited 类型填写)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_trace_msg_type (trace_id, message_id, feedback_type) COMMENT '幂等:同一 trace+消息+类型仅一条',
    KEY idx_user (user_id),
    KEY idx_product (product_id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用户反馈采集';
```

字段说明：
- `trace_id`：由 Agent Runtime 生成并透传（现状无 trace_id，属新增；历史消息无 trace 时以 `message_id` 参与幂等，`trace_id` 允许为 `-` 占位）。
- `user_id`：必填（匿名化只发生在向候选集转写时）；未登录请求不产生反馈。
- `feedback_type`：枚举行为——`like/dislike`（点赞/点踩，可带 `rating` 无意义）、`copy`（复制）、`adopt`（采纳）、`edited`（编辑后发送，`edited_content` 必填）、`retry`（重试/换一句）、`ignore`（忽略）、`manual`（人工评分，`rating` 必填 1-5）。

采集 API（设计）：`POST /api/ai/feedback`，body `{trace_id, message_id, feedback_type, rating?, edited_content?}`；服务端校验枚举与幂等键；响应 200 幂等成功（重复提交返回已存在记录），400 参数非法。

约束：入库前必须过脱敏（第 4 节）；`edited_content` 上限 2000 字符，超长截断。弱信号（停留时间等）不写入本表，只作为候选集评分附加项（第 5 节）。

## 3. ai_candidate_sample（候选训练样本）⭐P0

```sql
CREATE TABLE ai_candidate_sample (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sample_key        VARCHAR(64)  NOT NULL COMMENT '样本唯一键(幂等 upsert)',
    source_type       ENUM('chat','summary','manual','virtual')
                                   NOT NULL COMMENT '来源: 客服对话/搜索摘要/人工标注/合成样本',
    user_id           BIGINT       DEFAULT NULL COMMENT '用户ID;未授权时必须为 NULL(匿名化)',
    conversation_json MEDIUMTEXT   NOT NULL COMMENT '脱敏后的完整上下文(JSON)',
    user_msg          TEXT         NOT NULL COMMENT '用户消息(脱敏后)',
    assistant_msg     TEXT         NOT NULL COMMENT '模型回复(脱敏后)',
    feedback_stats    JSON         DEFAULT NULL COMMENT '反馈聚合统计 {like:int,dislike:int,copy:int,adopt:int,edited:int,retry:int,ignore:int,manual:int}',
    quality_score     DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '质量分 0-100(第5节规则)',
    dedup_hash        CHAR(64)     NOT NULL COMMENT 'sha256 去重哈希(规范化内容计算)',
    status            ENUM('pending','training','trained','excluded','failed')
                                   NOT NULL DEFAULT 'pending' COMMENT '样本状态机',
    model_version     VARCHAR(32)  DEFAULT NULL COMMENT '产出该回复的模型版本(如 deepseek-chat@2026-08-01 / champion-v3)',
    source_message_id BIGINT       DEFAULT NULL COMMENT '源消息 id(t_ai_chat_message 或导出记录)',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expires_at        DATETIME     DEFAULT NULL COMMENT 'TTL 过期时间,过期后禁止进入训练',
    UNIQUE KEY uk_sample_key (sample_key),
    KEY idx_dedup_hash (dedup_hash),
    KEY idx_status_score (status, quality_score),
    KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 候选训练样本';
```

字段说明：
- `sample_key`：`sha256(source_type + ':' + dedup_hash)[0:32]` + 8 位 day 时间戳（同内容跨天更新时重算）。幂等 upsert 依据：同 key 重复提交只更新 `feedback_stats`、`quality_score`、`model_version`、`updated_at`，不新增行。
- `status` 状态机：`pending`（可采样）→ `training`（训练中）→ `trained`（已用）；`excluded`（低质/删除/冲突/holdout 冲突，记录 reason）；`failed`（训练失败回溯）。
- `conversation_json`：脱敏后的完整上下文（对话域的 history 窗口 + 商品信息；摘要域的 keyword + 输入 context），用于溯源与重放，不直接进训练 prompt。
- `model_version`：P0 记录产出版本（DeepSeek 记为 `deepseek-chat@<日期>`，本地 worker 记 `champion-vX`/`qwen3-8b-distill@checkpoint-N`）。
- 导出：`--source-type` 过滤导出 JSONL；`chat` 类导出 `{user_msg, assistant_msg}`，`summary` 类导出 `{keyword, summary}`（兼容 `finetune_summary.py` 输入，见 [CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 4 节）。
- 导出批次格式（第 7 节幂等的载体）：每批导出生成 `batch_manifest.json`（批次 ID、`sample_key` 列表、内容指纹 sha256、导出时间）；重复导出同批次比对指纹，一致则复用。
- 导出示例（`summary` 类，脱敏后）：
  ```json
  {"keyword": "美食教程", "summary": "## 智能解读\n美食教程…\n## 平台发现\n平台共收录 12 个相关作品…"}
  ```
  导出示例（`chat` 类）：
  ```json
  {"user_msg": "这款手机电池能用多久？", "assistant_msg": "正常使用约 1 天，支持 65W 快充…"}
  ```
- 数据量参考（现状一次性产物量级，仅供容量规划）：`training_data.jsonl` 5029 行、`training_data_cleaned_train/val.jsonl` 2932/764 行；新候选集按每日增量估算（P0 上限 2000 条/轮采样，见 [CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 2 节）。

## 4. 脱敏规则（幂等，强制）

入库 `ai_feedback`/`ai_candidate_sample` 与训练导出前必须执行；脱敏函数必须是纯函数（同输入同输出），保证 `dedup_hash` 稳定可复算。

| 类型 | 匹配模式 | 掩码示例 |
|---|---|---|
| 手机号 | `1[3-9]\d{9}` | `138****8000` |
| 身份证 | `\d{15}(\d{2}[\dXx])?` | 15 位 `1101**********1`；18 位 `110101**********1X` |
| 邮箱 | `[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}` | `u***@dom***.com` |
| IP | `\d{1,3}(\.\d{1,3}){3}` | `8.134.***.***` |
| 银行卡 | `\d{13,19}`（前缀非 3/4/5/6 的号码跳过） | `6222********1234` |
| 密钥 | `sk-[A-Za-z0-9]{16,}` | `sk-2e82••••••••••` |

- 掩码算法：首尾保留固定位、中间 `*` 填充；密钥类整体掩码（如示例）。
- 幂等性要求：纯函数实现；`dedup_hash` 一律在**脱敏后**内容上计算。
- 实现与对齐：Java 侧 `com.douyin.ai.dataquality`（入库链路）+ Python 侧 `ai_pipeline.py`（训练入口二次校验）；同一规则表，两套实现以本表 + 单元测试对齐（AI-003/AI-004 DoD）。
- 现状缺口（事实）：`export_training_data.py` 导出无 PII 脱敏（且含明文 DB 密码，见 [SECURITY.md](./SECURITY.md)），该脚本不作为新闭环数据源。
- 敏感场景示例：`"电话 13812348000 邮箱 a@b.com 卡号 6222xxxxxxxx1234"` → `"电话 138****8000 邮箱 a***@b***.com 卡号 6222********1234"`；连续两次执行结果一致。

## 5. 去重与质量评分

**去重**：`dedup_hash = sha256(normalize(user_msg) + '\n' + normalize(assistant_msg))`；`normalize` = 去空白/全半角统一/小写。
冲突处理：同 `dedup_hash` 多源样本 → 保留 `quality_score` 最高者，其余 `excluded`（`reason=dup_of:<sample_key>`）；平分时按 `source_type` 优先级 `manual > chat > summary > virtual`。

**质量分 0-100**（规则实现于 dataquality）：

| 维度 | 规则 | 权重 |
|---|---|---|
| 长度 | 摘要类 40-400 字、对话类 10-600 字满分，越界线性扣分 | 20 |
| 重复度 | 文本内 4-gram 重复率 > 0.3 扣分 | 20 |
| 反馈加权 | `base 0`：like+8/次、copy+6、adopt+5、edited(更长的修正)+4、manual→`rating×6`；dislike−8、retry−5、ignore 0；累计 clamp [−30,+30] | 30 |
| 格式合法 | 摘要类必须含 `## 智能解读` 与 `## 平台发现` 段落；对话类角色完整 | 20 |
| 拒绝回答率 | 正常请求下含拒答词（"无法回答/抱歉/暂无"）扣分；摘要空结果场景豁免 | 10 |

总分 `= Σ(维度得分×权重)/100`，clamp 0-100。阈值（默认）：`< 60` → `excluded`；`≥ 60` → `pending`。隐式信号（停留时间等）只允许作为附加项 ±5，默认配置关闭（`QUALITY_IMPLICIT_ENABLED=false`）。

## 6. 样本溯源、删除与 TTL

- **溯源**：每行保留 `source_type`、`created_at`、`model_version`、`source_message_id`（关联 `t_ai_chat_message.id`）。
- **用户数据删除**：`DELETE /api/ai/user-data`（设计）删除该用户全部 `ai_feedback` 行，并将 `ai_candidate_sample` 中该 `user_id` 样本置 `status=excluded`（reason `user_deleted`）、`user_id` 置 NULL；已 `trained` 批次记录删除事件（审计），不回溯已收敛权重（宪法第 4 节）。
- **TTL**：`expires_at` 默认 `created_at + 90 天`（可配置）；训练采样条件含 `expires_at > NOW()`；过期样本置 `excluded`（reason `expired`）。
- **授权**：样本进入训练必须满足 `user_id IS NULL` 或授权标记有效；P0 简化：默认非授权，匿名化后才可训练。

## 7. 幂等汇总

| 操作 | 幂等键 | 行为 |
|---|---|---|
| feedback 采集 | `(trace_id, message_id, feedback_type)` | 唯一键冲突 → 幂等成功（返回已存在记录） |
| 候选样本 upsert | `sample_key` | 存在则合并统计更新，不新增 |
| 训练导出 | `(sample_key, 导出批次)` | 批次指纹化，重复导出结果一致 |
| 脱敏 | 纯函数 | 同输入同输出 |

## 8. 一致性要求（与本文档集其他部分）

- `trace_id` 生成/透传见 [ARCHITECTURE.md](./ARCHITECTURE.md) M1/M2（AI-002 引入）；本契约表结构不依赖其完成即可建（trace 字段允许占位），采集 API 联调依赖 AI-002 的 trace 透传。
- 样本导出格式喂给 [CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 4 节训练入口。
- 评分阈值与发布门禁的关系见 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 3 节。
- 本契约为设计；以 AI-003/AI-004 任务迁移文件为准（`migration_036`/`migration_037`）。