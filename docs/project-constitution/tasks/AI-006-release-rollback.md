# AI-006：Champion/Challenger、灰度发布、自动回滚与运营开关

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`planned`
- 依赖：AI-005（评测产物 `metrics.json` 可用）
- 拥有：`com.douyin.ai.release`（新建包）、`ai_ctrl`（配置/开关表）
- 禁止修改：RTC 域、用户未提交工作区

## 目标

- Champion/Challenger 版本管理：版本链记录（`ai_ctrl`），发布 promote / rollback 命令，对比基准为 Champion 评测结果。
- 灰度发布：`1% → 5% → 25% → 100%` 档位、每档观察 ≥ 24h、按 `user_id % 100` 分配、观察指标（错误率/p95/拒答率/占位率）。
- 自动回滚：越界即回滚（P0：错误率 > 1% 或 p95 > 基线 1.5× 持续 10 分钟）；回滚保留上一版本权重，**禁止删除旧权重**。
- 暂停/关闭开关：一键暂停训练、关闭自动学习、回滚上一模型（语义见 [../../ai-agent/AI_AGENT_CONSTITUTION.md](../../ai-agent/AI_AGENT_CONSTITUTION.md) 第 6 节）。

## 非目标

- 不做训练与评测本身（AI-005）。
- 不做多模型 A/B 之外的推荐/排序实验框架。
- 8B Challenger 仅可在远程 GPU 环境评测后进入灰度，本机不默认启用。

## 事实基线

- 现状无任何版本管理/灰度/回滚机制：`SearchSuggestionService` 的 8B→3B 回退是"启动时模型选择"，不是发布回滚；`distill_output/` 产物（checkpoint-500/915、merged_model）为一次性离线产物。
- Gateway 由 AI-002 提供 provider 切换点：回滚 = 切换权重/provider 引用 + 流量标置 0%。

## Definition of Done

- [ ] 门禁测试：Challenger 指标任一低于阈值 → 发布被拒（命令非零退出），事件留审计。
- [ ] 回滚测试：模拟灰度越界 → 自动回滚到 Champion，流量在 ≤5 分钟内全量切回；回滚后旧/失败权重文件仍然存在（不删除断言）。
- [ ] 灰度开关生效（`AI_GRAY_ENABLED` 与档位配置），分流按 `user_id % 100` 稳定可复现。
- [ ] 三个运营开关（暂停训练/关闭自动学习/回滚上一模型）读 `ai_ctrl` 生效，变更写审计事件。
- [ ] 版本链记录：promote 后上一 Champion 转为"上一版本"永久保留（清理需人工确认）。

## 交付物

- `com.douyin.ai.release`（ReleaseManager：promote/rollback/灰度/监控阈值、ReleaseGate）
- `ai_ctrl` 配置表（开关 + 当前生效版本 + 历史链）
- 回滚与门禁的自动化测试与审计记录格式