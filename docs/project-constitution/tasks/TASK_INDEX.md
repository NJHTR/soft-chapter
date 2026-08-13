# 实时媒体任务索引

状态含义：`planned` 计划中，`in_progress` 进行中，`review` 审查中，`verified` 已验证，`completed` 完成，`blocked` 外部阻断。

| ID | 任务 | 状态 | 依赖 | 主要交付物 |
|---|---|---|---|---|
| RTC-001 | 宪法、现状基线、模块边界、契约和路线图 | `completed` | 无 | `docs/project-constitution`、架构文档、ADR、schema、测试计划 |
| RTC-002 | LiveKit/coturn/SRS provider bootstrap 环境 | `completed` | RTC-001 | 配置、端口、健康检查、provider CLI smoke test；不依赖业务 token API |
| RTC-003 | RTC 控制面和通话领域 | `completed` | RTC-001 | CallSession、Participant、ACL、幂等事件、token API、webhook |
| RTC-004 | 1 对 1 LiveKit 适配器 | `planned` | RTC-003 | 设备管理、音频优先、接通/重连、通话记录 |
| RTC-005 | 群聊音视频 SFU 迁移 | `planned` | RTC-004 | 8 人基线、订阅策略、simulcast、active speaker |
| RTC-006 | 直播 WHIP/WHEP 迁移 | `planned` | RTC-002、RTC-003 | 主播 ingest、观众播放、HLS/HTTP-FLV fallback、单一 presence |
| RTC-007 | QoE、ABR、弱网和恢复 | `planned` | RTC-004、RTC-005、RTC-006 | stats、质量策略、ICE restart、降级/恢复 |
| RTC-008 | 录制和转码 | `planned` | RTC-005、RTC-006 | Egress/DVR、异步 FFmpeg、对象存储、审计 |
| RTC-009 | Legacy 退役和 native 收口 | `planned` | RTC-007、RTC-008 | 迁移桥、feature flag、退出报告、C++ 独立验证 |
| RTC-010 | 安全、负载、故障演练和发布 | `planned` | RTC-007、RTC-008、RTC-009 | NAT/弱网/长通话/负载/回滚门禁 |

## 波次交付门

### A：探索与契约

RTC-001 必须先明确事实基线、数据模型、版本化 envelope、媒体 provider port 和测试矩阵。没有这些内容，不能以“修几个前端参数”启动媒体实现。

### B：基础设施与控制面

RTC-002 先建立不依赖业务代码的真实 provider、TURN、SRS、端口和健康检查；RTC-003 并行建立控制面、token、房间权限和事件账本。两者完成后才允许接入客户端。

### C：媒体与客户端

RTC-004、RTC-005、RTC-006 按依赖分别联调；通话和直播不共用媒体协议，但共用鉴权、观测和质量规范。

### D：运营与收口

RTC-007 到 RTC-010 必须在真实浏览器、TURN、弱网、重连和故障环境中验证，最后才允许删除 legacy 路径。
