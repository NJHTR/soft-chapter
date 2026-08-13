# RTC-001：实时媒体宪法、基线与架构契约

## 状态

- 状态：`completed`
- 负责人：`/root`
- 分支：`dev/full`
- 提交：`85262e2 docs(RTC-001): 实时媒体宪法与架构基线`
- 依赖：无
- 允许修改：`docs/project-constitution/`、`docs/architecture/`、`docs/adr/`、`docs/contracts/`、`docs/testing/`、`docs/roadmap/`
- 禁止修改：已有 `src/`、`server/`、`streaming-engine/` 业务代码以及用户未提交改动

## 目标

把直播、1 对 1 通话和群聊音视频从当前粗糙实现拆成长期模块；明确 WebRTC、SFU、直播媒体服务、控制面、质量和安全边界；建立后续智能体可执行的任务和验证门禁。

## 非目标

- 本任务不接入 LiveKit、SRS 或 coturn。
- 本任务不修改现有 `Call.vue`、WebCodecs、Java controller 或 C++ stub。
- 本任务不宣称当前 WebRTC 已可用，也不把未部署的 compose 服务视为通过。

## 事实基线

- 当前直播主路径是 WebCodecs + `/ws/live` 自定义消息，前后端文本/二进制处理不一致。
- 当前 offer API 回显 SDP，C++ WebRTC streamer 没有实际 PeerConnection。
- 当前通话是每参与者一个 P2P 连接的 mesh，只有公共 STUN，没有 TURN/SFU。
- 当前前端类型检查和 Vite 构建可通过，但缺少浏览器、后端、TURN 和媒体服务 E2E。

## Definition of Done

- [x] 宪法、任务协议、AgentGit 协议和状态文件存在并可读。
- [x] 模块所有权、依赖关系和禁止越界规则明确。
- [x] LiveKit/SRS/coturn 的选型和职责有 ADR。
- [x] Call 状态机、参与者状态、信令 envelope 和兼容迁移方案有文档/schema。
- [x] 质量目标、QoE 指标、弱网策略和测试矩阵有文档。
- [x] 已知硬阻断、未验证项和工作区风险写入日志。
- [x] 文档变更独立提交，状态文件记录真实提交哈希。
