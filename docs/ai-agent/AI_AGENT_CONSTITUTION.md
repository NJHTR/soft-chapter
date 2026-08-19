# AI 持续学习宪法

owner: main-dev-agent, created: 2026-08-14

## 1. 目标与适用范围

本宪法约束平台内所有"用运行数据改进模型参数"的行为，覆盖两条现有 AI 链路：

- **AI 客服对话**：现状由 `AIController` 直连 DeepSeek（无超时/重试/取消/流式/trace_id，见 [ARCHITECTURE.md](./ARCHITECTURE.md) 第 1 节现状基线）。
- **AI 搜索摘要**：现状由 `SearchSuggestionService` 以 `ProcessBuilder` 拉起 `search_summary.py --serve` 常驻进程（3B 本机可跑、8B 蒸馏本机离线不可跑，见 [LOW_RESOURCE_OPTIMIZATION.md](./LOW_RESOURCE_OPTIMIZATION.md) 第 1 节）。

第三条链路——"prompt / 知识库 / 召回策略的修改"——受本宪法数据边界约束，但不涉及权重变化，不受训练门禁约束。

管辖对象：`com.douyin.ai.*`（Java）、`server/python/`（Python 训练与推理）、`docs/ai-agent/`（本文档集）；由任务族 [AI-000-index](../project-constitution/tasks/AI-000-index.md) 执行。

## 2. 核心原则

**P1 受控持续学习闭环**。模型改进只能走"采集 → 脱敏去重 → 评分 → 候选集 → 异步训练 → 评测 → 门禁 → 灰度发布"闭环（[CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 1 节）。任何绕过闭环的参数修改一律禁止，包括但不限于：直接在线上进程里替换/修改权重文件、把线上流量即时喂给优化器、以"实验"名义在测试机加载真实用户数据训练后未走门禁直接上线。

**P2 禁止在线无监督改权重**。禁止模型在服务用户请求的同时用原始用户流量无监督地修改自身权重；禁止把隐式信号（停留时长、浏览轨迹等）直接当正确标签（第 4 节）。权重更新只允许发生在离线训练进程，且必须由候选样本集驱动（P6）。

**P3 训练必须异步**。训练、评测、门禁决策不得阻塞用户请求；HTTP 请求线程内不允许出现模型优化计算。训练默认低峰期调度（[CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 9 节），验收口径为该文档第 8 节。

**P4 新模型必须过固定评测集与安全规则才可发布**。未通过 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 定义的门禁（holdout 评测指标 ≥ 阈值、安全规则扫描、与 Champion 对比、人工确认或显式配置授权）的模型，不允许进入任何线上流量（含灰度首档）。

**P5 模型不能单独给自己打分**。发布决策信号必须来自三方：① 规则化指标（长度/重复度/格式/延迟等，可编程计算）；② 固定 holdout 测试集（版本化、防污染，见 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 2 节）；③ 与历史模型（Champion）的对比。必要时引入独立教师模型（如 DeepSeek 蒸馏评测）只用于评测、不参与线上。模型自评/置信度输出只作辅助参考，不得作为发布依据；训练 loss 下降不得作为发布理由（现状 `distill_output/train_metrics.json` 仅记录 train/eval loss，不是发布证据）。

**P6 样本必须可追溯**。任何进入训练集的样本必须保留：来源类型、产生时间、用户授权状态、质量分、模型版本、源消息 ID（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 6 节）。不可溯源样本不得训练；`dedup_hash` 必须基于脱敏后内容计算，保证可复算。

**P7 运营开关必须可用**。平台提供并常备三个开关：一键暂停训练、回滚上一模型、关闭自动学习（第 7 节）。开关只依赖数据库/配置与 Gateway 切换点，不依赖任何模型进程存活；开关失效视为 P0 故障。

## 3. 判定与评审

- 发布判定由"门禁规则 + 评测产物"驱动，无人为"我觉得更好"豁免；Challenger 与 Champion 使用同一评测命令、同一 holdout 版本、同一指标定义（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 4 节）。
- 连续 3 次门禁失败触发训练自动暂停，需人工决策（防循环烧预算）。
- 所有判定留痕：评测产物 `metrics.json`、发布/回滚事件、开关变更事件进入审计（第 5 节、[SECURITY.md](./SECURITY.md) 第 5 节）。
- 评审证据链：一次完整发布必须能回答"改了哪些权重、用什么数据、谁评测、谁确认、何时灰度、何时生效"六个问题；任一问无法回答则该发布视为未授权变更。

**打分与发布的主体分工**（防自我背书）：
- 训练侧（M5）负责产出 checkpoint 与训练报告（loss、数据指纹），不负责判定可发布性；
- 评测侧（M6）负责跑 holdout 与规则指标，输出 `metrics.json`，不与训练侧共享代码路径；
- 发布侧（M6 release）只依据 `metrics.json` 与门禁规则做 promote/rollback，不参与训练参数选择；
- 模型自身输出（自评/置信度）只作为辅助参考，不进入门禁判定（P5）。

## 4. 数据边界

- **候选样本集唯一**：训练只允许消费 `ai_candidate_sample`（已脱敏 + 去重 + 授权校验 + 质量评分）。禁止直接以 `t_ai_chat_message` 全量聊天记录投喂训练；该表只可被采集链路读取。
- **隐式信号**：停留时间、浏览时长等行为信号**不能**直接当正确标签；只允许作为质量分附加项（±5 分上限、默认开关关闭，见 [DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 5 节）。
- **授权**：样本进入训练前必须满足 `user_id IS NULL` 或授权标记有效；P0 简化为默认非授权（匿名化后才可训练）。
- **删除**：用户可删除自己的数据。删除接口语义（删 feedback、候选样本置 `excluded` 且匿名化）见 [DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 6 节；删除后 24h 内生效于候选集；已训练批次只记录删除事件，不要求回滚已收敛权重。
- **TTL**：样本 `expires_at` 默认 90 天，过期禁止进入训练（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 6 节）。
- **PII 脱敏是训练入口硬前置**：六类模式（手机号/身份证/邮箱/IP/银行卡/密钥）幂等脱敏，规则见 [DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 4 节。

## 5. 发布门禁

- 线上永远只有一个 Champion；新模型以 Challenger 身份通过门禁后成为新 Champion，旧 Champion 转"上一版本"保留。
- 灰度默认 `1% → 5% → 25% → 100%`，每档观察 ≥ 24h（可配置），分流按 `user_id % 100` 稳定分配；观察指标与阈值见 [EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 6 节。
- **自动回滚**：错误率 > 1% 或 p95 > 基线 1.5× 持续 10 分钟等越界条件触发，无需人工。
- **保留旧权重**：失败回滚必须保留上一版本权重；任何流程（含存储整理）禁止删除旧/失败权重，清理需人工确认（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 6 节）。
- **灰度期间的数据归属**：Challenger 流量产生的反馈照常采集（`ai_feedback`），但**不转候选集、不参与本轮训练**——防止用 Challenger 自身输出训练自身（自我放大/偏差）；只有 Champion（线上服役模型）流量进入候选集。该规则同时服务 P2（禁止无监督自训）与 P5（自评不进入门禁）。
- **门禁与数据快照**：发布决策基于评测时刻的权重与评测集；训练期间候选集继续增长不影响已提交评测的结果（评测产物含数据指纹，见评测文档第 7 节）。

## 6. 安全基线

- **密钥环境变量化**：DeepSeek、DB、邮箱、MinIO、JWT、Redis 只允许环境变量注入，禁止落仓库；现状硬编码密钥登记与处置见 [SECURITY.md](./SECURITY.md)。
- **日志**：不得记录敏感内容——不回显完整密钥（只允许掩码或 `[redacted]`）、不打印原始消息中的 PII 字段；trace 日志过脱敏规则。
- **PII 脱敏**：入库与训练均为强制项，规则幂等（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 4 节）。
- **隔离**：租户/用户数据隔离；未授权样本 `user_id` 必须为 NULL；训练读取路径不得绕过授权标记。
- **fail-closed**：鉴权/授权/脱敏组件不可用时相关链路拒绝处理，不得降级为明文入库（[SECURITY.md](./SECURITY.md) 第 5 节）。
- **密钥生命周期**：新密钥只进环境变量与部署配置；密钥产生 → 使用 → 轮换 → 作废全程有记录（轮换计划见 [SECURITY.md](./SECURITY.md) 第 4 节）；疑似泄露按该文件第 6 节响应。

## 7. 运营开关与回滚语义

| 开关 | 实现（目标设计） | 生效语义 | 恢复方式 |
|---|---|---|---|
| 一键暂停训练 | `AI_TRAINING_ENABLED=false`（`ai_ctrl`/env） | 训练任务在下一个 checkpoint 边界停止并记录 `stopped`；排队任务取消 | 显式 `--resume-from <checkpoint>` 或重新调度 |
| 关闭自动学习 | `AI_AUTO_LEARNING_ENABLED=false` | 停止候选集构建与训练调度；反馈采集仍继续（只落 `ai_feedback`） | 重新开启后从上次候选集状态继续 |
| 回滚上一模型 | `AI_MODEL_VERSION=<上一版本>` | Gateway 立即切换权重/provider；灰度流量 ≤5 分钟内全量切回 | 再次 promote 或人工指定版本 |

开关状态持久化并出现在启动日志（只记开关名）；变更视为运维事件留审计（[SECURITY.md](./SECURITY.md) 第 5 节）。

## 8. 与现有工程边界

- RTC 域（`server/.../rtc/`、`src/modules/rtc/`、`docs/contracts/`、`docs/architecture/`）已提交，**不受本宪法改动**；直播/后台/搜索主链路/原生 streaming-engine 的实现决策不在本文档集管辖。
- 用户未提交工作区（`admin/`、live/engine/websocket/controller、`src/pages/live`、`src/utils/streaming`、`streaming-engine`、`docs/runtime`、`docs/verification`、`server/python/autodl_upload.zip`）**只读**，本文档集不描述其可修改能力。
- `docs/verification/AI_PIPELINE.md` 是 C++ streaming-engine 视觉 AI 验证报告（ONNX/TensorRT 为 stub），**与摘要学习闭环无关**；本文档集与后续 AI 任务不得把 stub 宣称真实能力。
- 目录归属：AI 代码拥有 `com.douyin.ai.*`、`server/python/`、`docs/ai-agent/`；与 RTC 拥有目录不重叠（[AI-000-index](../project-constitution/tasks/AI-000-index.md)）。
- `application.yml` 仅允许 AI-001 做密钥 env 化这一项修改；其余配置改动走各自任务 DoD。

## 9. 违规与豁免

- 违反 P1-P7 的变更视为未授权改动：主智能体审查并回退，风险记入 [SECURITY.md](./SECURITY.md) 与任务状态。
- 豁免仅限：纯提示词文案调整（不改变权重与数据流）、日志/文档修改；豁免项仍须遵守第 6 节与数据边界。
- 本宪法修订需在 `docs/ai-agent/` 追加修订说明，并在任务索引置顶标出。

## 10. 生效与修订记录

- v0：2026-08-14 由 AI-001 起草入库；依赖 AI-001（[AI-001-foundation](../project-constitution/tasks/AI-001-foundation.md)）。
- 状态：design（代码尚未实现，本文件描述目标语义，不宣称已具备任何能力）。
- 修订规则：任何对本宪法的修改须在同一 PR/提交中附"修订说明"小节（改了什么、为什么、影响哪些任务），并在 [AI-000-index](../project-constitution/tasks/AI-000-index.md) 顶部注明最新修订版本。
- 例外处理：灰度期数据归属（第 5 节）、删除竞态（删除请求与候选集构建并发时，以"删除优先"为准，删除事件在构建完成后 24h 内兜底重扫）等边界语义由实施任务在 DoD 中落测试用例。

## 附录 A：术语表

| 术语 | 定义 |
|---|---|
| 样本 | 一条 (user_msg, assistant_msg) 或 (keyword, summary) 配对，带元数据（来源/时间/授权/质量分/版本） |
| 候选样本集 | `ai_candidate_sample` 中 `status=pending` 且质量为 `≥60` 的样本集合，是训练唯一数据入口 |
| Champion / Challenger | 线上服役模型 / 待验证候选模型（[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 4 节） |
| trace_id | 一次请求的链路标识，贯穿 Runtime → Gateway → provider → 反馈采集 |
| holdout | 固定、版本化、防污染的评测集（第 5 节与评测文档第 2 节） |
| replay buffer | 历史高置信样本子集，混入训练防遗忘（[CONTINUOUS_LEARNING_DESIGN.md](./CONTINUOUS_LEARNING_DESIGN.md) 第 3 节） |
| 门禁 | 发布前的指标 + 安全规则 + 人工确认检查点 |
| fail-closed | 安全组件不可用时拒绝处理而非降级（[SECURITY.md](./SECURITY.md) 第 5 节） |