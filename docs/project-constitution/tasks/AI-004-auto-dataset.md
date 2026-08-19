# AI-004：自动候选数据集构造（脱敏/去重/筛选/打分）

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`planned`
- 依赖：AI-003（feedback 采集可用；表结构联调）
- 拥有：`com.douyin.ai.dataquality`（新建包）；迁移文件 `server/sql/migration_037_ai_candidate_sample.sql`
- 禁止修改：RTC 域、用户未提交工作区

## 目标

- 建 `ai_candidate_sample` 表（DDL 见 [../../ai-agent/DATA_CONTRACT.md](../../ai-agent/DATA_CONTRACT.md) 第 3 节，AI-004 拥有 `migration_037`）。
- 自动管线：脱敏（幂等）→ 去重（`dedup_hash` sha256）→ 筛选 → 打分（0-100 五维规则）→ 幂等 upsert（`sample_key`）。
- 低质量隔离：`quality_score < 60` 置 `excluded`（reason 记录）；holdout 冲突样本隔离（`excluded, reason=holdout_dup`）。
- 候选集导出 JSONL（`chat` 类 `{user_msg, assistant_msg}` / `summary` 类 `{keyword, summary}`，兼容训练输入）。

## 非目标

- 不训练（AI-005）不发布（AI-006）。
- 不做自动扩样/合成样本生成（`_virtual` 合成属现状一次性蒸馏产物，不纳入本任务自动管线）。
- 不替换/不修改 `export_training_data.py`（含明文密钥与无脱敏问题，仅登记风险，见 [../../ai-agent/SECURITY.md](../../ai-agent/SECURITY.md)）。

## 事实基线

- 现状无候选集表与评分管线；评分规则定义于 [../../ai-agent/DATA_CONTRACT.md](../../ai-agent/DATA_CONTRACT.md) 第 5 节（长度 20 / 重复度 20 / 反馈加权 30 / 格式 20 / 拒绝回答率 10）。
- 训练数据格式兼容：现状 `finetune_summary.py` 消费 `{keyword, summary}` JSONL；`distill/` 产物（`training_data*.jsonl`、`_virtual` 样本）为一次性离线数据，可作评测/参考，不直接进自动管线。
- 脱敏规则表（六类模式）与 AI-003 共享，实现必须一致（纯函数 + 单测对齐）。

## Definition of Done

- [ ] `migration_037` 落地 `ai_candidate_sample`（`uk_sample_key`、`idx_dedup_hash`、`idx_status_score`、`idx_expires`）。
- [ ] 脱敏单测（与 AI-003 同一规则表，六类模式 + 幂等）、去重单测（normalize + sha256 + 冲突保留高分、平分按 `manual > chat > summary > virtual`）。
- [ ] 评分单测：五维权重、clamp 0-100、阈值 60、隐式信号开关（默认关）。
- [ ] `sample_key` 幂等 upsert：同 key 重复提交只合并 `feedback_stats/quality_score/model_version`。
- [ ] 候选集导出命令输出 JSONL，`--source-type` 过滤，导出批次指纹（重复导出结果一致）。
- [ ] 管线以批量任务运行（独立调度，不进入 HTTP 请求路径，见 [../../ai-agent/CONTINUOUS_LEARNING_DESIGN.md](../../ai-agent/CONTINUOUS_LEARNING_DESIGN.md) 第 8 节）。

## 交付物

- `com.douyin.ai.dataquality`（Desensitizer / Deduplicator / QualityScorer / CandidateBuilder / Exporter）
- `migration_037_ai_candidate_sample.sql`、批量构建任务与导出命令