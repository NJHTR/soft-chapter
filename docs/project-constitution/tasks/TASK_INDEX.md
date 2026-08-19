# 全项目任务索引

状态含义：`planned` 计划中，`in_progress` 进行中，`review` 审查中，`verified` 已验证，`completed` 完成，`blocked` 外部阻断。

| ID | 任务 | 状态 | 依赖 | 主要交付物 |
|---|---|---|---|---|
| RTC-001 | 宪法、现状基线、模块边界、契约和路线图 | `completed` | 无 | `docs/project-constitution`、架构文档、ADR、schema、测试计划 |
| RTC-002 | LiveKit/coturn/SRS provider bootstrap 环境 | `completed` | RTC-001 | 配置、端口、健康检查、provider CLI smoke test；不依赖业务 token API |
| RTC-003 | RTC 控制面和通话领域 | `completed` | RTC-001 | CallSession、Participant、ACL、幂等事件、token API、webhook |
| RTC-004 | 1 对 1 LiveKit 适配器 | `completed` | RTC-002、RTC-003 | 设备管理、音频优先、接通/重连、通话记录 |
| RTC-005 | 群聊音视频 SFU 迁移 | `completed` | RTC-004 | 8 人基线、订阅策略、simulcast、active speaker |
| RTC-006 | 直播 WHIP/WHEP 迁移 | `in_progress` | RTC-002、RTC-003 | 主播 ingest、观众播放、HLS/HTTP-FLV fallback、单一 presence |
| RTC-007 | QoE、ABR、弱网和恢复 | `planned` | RTC-004、RTC-005、RTC-006 | stats、质量策略、ICE restart、降级/恢复 |
| RTC-008 | 录制和转码 | `planned` | RTC-005、RTC-006 | Egress/DVR、异步 FFmpeg、对象存储、审计 |
| RTC-009 | Legacy 退役和 native 收口 | `planned` | RTC-007、RTC-008 | 迁移桥、feature flag、退出报告、C++ 独立验证 |
| RTC-010 | 安全、负载、故障演练和发布 | `planned` | RTC-007、RTC-008、RTC-009 | NAT/弱网/长通话/负载/回滚门禁 |
| RTC-011 | SFU/TURN/客户端容量观测、Admission 与压测 | `planned` | RTC-007 | QoE 基线、容量报告、room admission、压测 harness |
| RTC-012 | 客户端选择性订阅与媒体减载 | `planned` | RTC-005、RTC-007 | publication 生命周期、simulcast/dynacast、可见性、音频优先 |
| RTC-013 | LiveKit 多节点、Redis 路由与 TURN 区域池 | `planned` | RTC-011 | room placement、节点 drain、扩容和故障演练 |
| RTC-014 | Stage + audience 大规模直播分层 | `planned` | RTC-006、RTC-013 | SRS/CDN audience、上麦、breakout room |
| RTC-015 | 受控 1 对 1 P2P 实验与 SFU 回退 | `planned` | RTC-004、RTC-007 | 独立信令、ICE 探测、灰度、质量和隐私验收 |

## AI 持续学习任务索引

| ID | 任务 | 状态 | 依赖 | 主要交付物 |
|---|---|---|---|---|
| AI-001 | AI 宪法、架构、数据契约、评测发布、低资源优化、安全文档；密钥 env 迁移与风险记录 | `completed` | 无 | `docs/ai-agent/` 七文档、application.yml 密钥 env 化、SECURITY.md 轮换风险记录 |
| AI-002 | Agent Runtime 与 Model Gateway | `planned` | AI-001 | runtime(意图/工具/上下文预算/超时重试取消/trace_id/幂等/权限)、gateway(local worker 管理 + DeepSeek fallback + OpenAI-compatible + 健康检查 + 流式 + p95) |
| AI-003 | 反馈与候选样本数据模型 | `planned` | AI-001 | ai_feedback 采集 API、migration_036、授权/删除/TTL |
| AI-004 | 自动脱敏/去重/筛选/打分 | `planned` | AI-003 | ai_candidate_sample(migration_037)、PII 脱敏、去重、质量评分、候选集导出 |
| AI-005 | 离线评测与受控训练编排 | `completed` | AI-004 | holdout 固定集、评测命令、dry-run/受控 LoRA 训练(预算/早停/checkpoint/resume) |
| AI-006 | Champion/Challenger 灰度发布与回滚 | `planned` | AI-005 | 对比评测、灰度、自动回滚、暂停/关闭开关、旧权重保留 |

AI 任务族与 RTC 任务族独立:拥有目录不重叠(Java `com.douyin.ai`、`server/python/ai_pipeline.py|ai_worker.py`、`docs/ai-agent/`、迁移 036+),共享服务器资源但禁止跨越任务边界改动对方文件。

## 波次交付门

### A：探索与契约

RTC-001 必须先明确事实基线、数据模型、版本化 envelope、媒体 provider port 和测试矩阵。没有这些内容，不能以“修几个前端参数”启动媒体实现。

### B：基础设施与控制面

RTC-002 先建立不依赖业务代码的真实 provider、TURN、SRS、端口和健康检查；RTC-003 并行建立控制面、token、房间权限和事件账本。两者完成后才允许接入客户端。

### C：媒体与客户端

RTC-004、RTC-005、RTC-006 按依赖分别联调；通话和直播不共用媒体协议，但共用鉴权、观测和质量规范。

### D：运营与收口

RTC-007 到 RTC-010 必须在真实浏览器、TURN、弱网、重连和故障环境中验证，最后才允许删除 legacy 路径。

## 当前执行指针（2026-08-17）

- 当前任务：`RTC-006`。
- 当前波次：C（SRS WHIP/WHEP 媒体迁移）与 D（浏览器播放、控制 WS 和质量基线）。
- 已完成的契约：控制面 OpenAPI、LiveKit webhook 事件账本、错误码和 `douyin.realtime.v1` 信令 schema。
- 尚未满足的发布门禁：真实双浏览器接通、TURN relay、官方 webhook 端到端回调和 QoE 报告。
- RTC-004/005 的控制面与代码路径已完成，但真实双浏览器/媒体 provider 验收仍记录在对应任务的 review gate 中。
- RTC-006 在真实 WHIP/WHEP、短期媒体授权、presence 幂等和 provider 故障验证完成前保持 `in_progress`，不得因为前端能编译而标记完成。

---

## Phase 3：管理后台（ADM）

| ID | 任务 | 状态 | 依赖 |
|---|---|---|---|
| ADM-001 | 审核工作流状态机（待审/通过/驳回/申诉） | `planned` | 无 |
| ADM-002 | 实时看板 WS 稳定性和鉴权（修 B-007） | `planned` | B-007 |
| ADM-003 | 权限分级（超管/运营/客服） | `planned` | ADM-001 |
| ADM-004 | 直播违规实时告警 | `planned` | RTC-006 |
| ADM-005 | 数据导出和报表 | `planned` | ADM-001 |

---

## Phase 4：电商支付收口（SHOP）

| ID | 任务 | 状态 | 依赖 |
|---|---|---|---|
| SHOP-001 | 支付回调签名验证完整测试 | `planned` | 无 |
| SHOP-002 | 退款状态机（申请/审核/打款/关闭） | `planned` | SHOP-001 |
| SHOP-003 | 钱包对账报表 | `planned` | SHOP-002 |
| SHOP-004 | 幂等键覆盖率审计 | `planned` | SHOP-001 |
| SHOP-005 | 支付安全审计（OWASP 专项） | `planned` | SHOP-004 |

---

## Phase 5：搜索与推荐升级（SRC/REC）

| ID | 任务 | 状态 | 依赖 |
|---|---|---|---|
| SRC-001 | Elasticsearch 接入（替换 MySQL LIKE） | `planned` | 无 |
| SRC-002 | 热词实时更新（Kafka → ES） | `planned` | SRC-001 |
| REC-001 | A/B 实验框架 | `planned` | 无 |
| REC-002 | 冷启动策略 | `planned` | REC-001 |
| REC-003 | 推荐解释性日志 | `planned` | REC-001 |

---

## Phase 6：发布门禁与运营（OPS）

| ID | 任务 | 状态 | 依赖 |
|---|---|---|---|
| OPS-001 | 全链路压测 | `planned` | RTC-010 |
| OPS-002 | 监控告警完整性 | `planned` | OPS-001 |
| OPS-003 | 灰度发布框架 | `planned` | OPS-001 |
| OPS-004 | 回滚演练手册 | `planned` | OPS-003 |
| OPS-005 | 安全渗透测试 | `planned` | OPS-002 |
