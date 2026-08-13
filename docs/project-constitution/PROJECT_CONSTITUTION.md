# Douyin 实时媒体项目宪法

**版本：** 1.1
**状态：** 生效
**生效日期：** 2026-08-13
**适用范围：** 直播、1 对 1 音视频通话、群聊音视频、连麦、录制、转码以及相关前端、Spring Boot、媒体服务和原生引擎。

这份宪法是实时媒体模块的长期约束。它优先于局部实现偏好，也优先于“先做出来再重构”的短期目标。与现有代码冲突时，必须在任务文件和工作日志中记录冲突、迁移方案和退出时间。

## 1. 项目目标

建立可观测、可恢复、可扩展的实时媒体平台：

- 直播：主播一次推流，媒体服务负责分发；互动低延迟与大规模观看使用不同出口。
- 1 对 1 通话：提供可靠的音频优先、设备切换、弱网恢复和通话记录。
- 群聊通话：默认使用 SFU，每个客户端只维护一条上行媒体连接，首期限制 8 人并可扩展到 16 人。
- 质量：用真实分辨率、帧率、码率、首帧、延迟、抖动、丢包和冻结率验收，不用滤镜或超分掩盖源质量。
- 演进：业务代码依赖稳定端口和适配器，媒体供应商可替换，控制面不绑定具体 WebRTC SDK。

## 2. 不可违反的原则

### 2.1 控制面与媒体面分离

Spring Boot、数据库和聊天 WebSocket 只负责身份、房间、权限、邀请、信令、状态、审计和指标。它们不得转发 RTP、视频帧、音频帧或 base64 媒体，也不得通过回显 SDP 冒充 WebRTC 协商。

媒体面由 LiveKit SFU（通话和互动连麦）或 SRS（直播 ingest/协议分发）承担；一项能力只能有一个生产主路径。MediaMTX 只能在明确的替代部署 profile 中启用，不能与 SRS 同时争用同一端口和职责。

### 2.2 WebRTC 实现必须是真实协商

任何公开为 WebRTC 的接口都必须完成真实的 SDP、ICE、DTLS-SRTP 和媒体轨道验证。固定 SDP、`connected=true`、空 `send_packet()`、无库链接的 C++ 占位实现都不能进入生产路径；占位代码必须标记为实验或禁用。

### 2.3 SFU 是群聊和连麦默认拓扑

P2P 仅允许作为受控的 1 对 1 降级或本地实验，必须有明确人数上限和指标。群聊、多人连麦不能继续使用当前的多 PeerConnection mesh 作为主路径。MCU 仅用于录制合屏、电话网关或旧终端兼容，不用于常规互动通话。

### 2.4 信令契约先行、版本化、幂等

应用控制事件使用 `douyin.realtime.v1` envelope，包含 `event_id`、`call_id/room_id`、发送者、目标、序号、时间和 payload。LiveKit 客户端 SDK 自己完成 provider 内部的 SDP/ICE 信令，不经过业务 WebSocket；schema 中的 `rtc.*` 只代表受控 P2P fallback 或 legacy bridge，不能作为 LiveKit 主路径实现指南。服务端是状态权威，校验成员关系、角色、状态迁移、顺序、TTL、重放和频率。旧 `type/signal_type/data` 与 CSV `to_user_ids` 只能由迁移适配器双读，不能在新模块继续扩散。

### 2.5 媒体质量必须基于能力协商

浏览器能力通过 `getCapabilities()`、`RTCRtpSender` 参数和已等待的 `isConfigSupported()` 进行协商。H.264/VP8 + Opus 是兼容基线，AV1 只能是可选增强，H.265 不能作为浏览器 WebRTC 基线。任何自定义 WebCodecs 协议都必须有完整的配置、序号、时间戳、关键帧和长度校验；默认生产路径使用原生 WebRTC RTP。

### 2.6 弱网先保音频、恢复必须可重入

根据 RTT、jitter、丢包、可用发送码率和冻结率降层、降分辨率、降帧率，必要时关闭视频但保留 Opus 音频。断线必须进入 `RECONNECTING`，执行有上限的 ICE restart/rejoin 和指数退避；重复事件不能产生重复房间、重复记录或重复计数。

### 2.7 安全和隐私是媒体功能的一部分

房间和轨道权限由服务端校验；短期 token、TURN 临时凭据、Origin allowlist、信令限流、日志脱敏和录制同意不可后补。媒体 URL、token 和用户隐私不能写入普通日志。生产环境禁止 `*` Origin 和永久公共 STUN 作为唯一穿透方案。

### 2.8 可观测性是 Definition of Done

每条媒体会话至少记录采集分辨率/帧率、编码帧率、发送码率、RTT、jitter、丢包、丢帧、PLI/FIR、ICE/连接状态、首帧/接通耗时、冻结率和端到端延迟。客户端、SFU、TURN、控制面必须能按 `trace_id`、`room_id`、`call_id` 关联。

### 2.9 录制和转码异步化

互动媒体默认不实时 MCU。录制使用 LiveKit Egress、SRS DVR 或 FFmpeg worker 异步完成，保存轨道元数据、同意状态、保留期限、删除审计和重试幂等键。

### 2.10 迁移优先于平行堆叠

当前 `/ws/live` 自定义帧、Call.vue 内部 mesh、WebCodecs WebSocket 和 C++ WebRTC stub 都只能作为 `legacy-bridge`。桥接必须有功能边界、监控、回滚方式和退役日期；禁止在 legacy 路径新增质量或业务能力。

## 3. 模块所有权

| 模块 | 责任 | 主要目录 | 禁止越界 |
|---|---|---|---|
| `rtc-domain` | CallRoom、Participant、状态机、权限策略、生命周期 | `server/.../rtc`（目标）、`docs/architecture` | 不依赖 Vue、LiveKit SDK 或数据库细节 |
| `rtc-signaling` | 版本化信令、路由、鉴权、幂等、重放、重连 | `server/.../websocket`、`src/utils/rtc`（目标） | 不传媒体字节、不修改 SDP |
| `rtc-media-adapter` | LiveKit token、房间/轨道适配、webhook | `server/.../rtc/provider`、`src/.../rtc` | 业务组件不得散落 `new RTCPeerConnection` |
| `ice-edge` | coturn、短期凭据、ICE 诊断和容量 | `deploy/rtc`（目标）、运维文档 | 不在前端硬编码生产凭据 |
| `live-domain` | 直播房间、主播生命周期、观看存在、审核和 URL | `server/.../controller/service`、`src/pages/live` | 不在 Spring WebSocket 转发媒体 |
| `live-media` | WHIP/SRT/RTMP ingest、WHEP/WebRTC、HLS/HTTP-FLV | `deploy/streaming`、媒体服务配置 | 不与通话 SFU 混用职责 |
| `media-quality` | 编解码、simulcast/ABR、QoE、弱网策略 | `src/utils/rtc`、`server/.../metrics` | 不用超分代替真实源质量 |
| `chat-persistence` | 私聊/群聊、消息投影、outbox | `server/.../service/mapper/sql` | 不拥有 CallSession/CallEvent，不把聊天消息当通话真相 |
| `rtc-persistence` | CallSession、Participant、CallEvent、provider webhook ledger | `server/.../rtc`（目标）、`server/sql`（目标） | 不把 provider 细节暴露给聊天模块 |
| `rtc-frontend` | 状态 store、设备、provider adapter、参与者视图、控制 | `src/components/Call.vue`（迁移期）、`src/modules/rtc`（目标） | 不持有服务端权威状态 |
| `native-engine` | 可选采集、滤镜、编码和 ingest adapter | `streaming-engine`、`server/.../engine` | JNI 不得阻断浏览器主路径 |
| `observability-security` | 指标、审计、告警、权限、隐私 | `docs/runbooks`、后端配置、监控配置 | 未测量的能力不能宣称完成 |

## 4. 工程门禁

合并任何实时媒体任务前，必须满足：

1. 任务文件、契约变更和架构决策已更新。
2. 业务边界、拥有目录和禁止修改目录清晰。
3. 单元/契约测试覆盖正常、重复、乱序、过期和权限失败路径。
4. 至少一条真实浏览器路径完成 SDP/ICE/媒体轨道验证；不能只看类型检查。
5. TURN、弱网、重连、设备拒绝和降级行为有验证记录。
6. 指标、日志脱敏、回滚和退役策略已经写入文档。
7. 变更只包含本任务文件，提交信息和验证命令完整。

## 5. 当前硬阻断（不得通过调参掩盖）

- `StreamController` 的 WebRTC offer 当前回显 SDP，不是真实协商。
- C++ `WebRTCStreamer` 没有实际 WebRTC 库或媒体轨道，全部为占位。
- `/ws/live` 是文本处理器，却被前端当作二进制媒体总线；控制和媒体还会重复建连接。
- `WebCodecsSender` 未使用 `EncodedVideoChunk.copyTo()`，且调用了不存在的 `getVideoTrack()`。
- `WebCodecsPlayer` 未正确等待 codec capability；播放器和发送器的自定义协议未形成端到端契约。
- `Call.vue` 只有公共 STUN、mesh 拓扑，远端视频 ref 也没有稳定绑定 `srcObject`。
- `StreamingEngine.isNativeAvailable()` 的 `|| true` 可能在 DLL 缺失时触发 JNI 崩溃。

这些问题在修复前，不能把“清晰度优化”定义为提高分辨率或码率。

## 7. 治理、版本和弃用

- 宪法由主智能体维护；新增或修改不可逆架构决策必须新增 ADR，并在任务索引中建立依赖。
- `douyin.realtime.v1` 只允许向后兼容新增可选字段；删除、改语义或改变必填字段必须创建 `v2`，并保留双读窗口。
- 每个 legacy 路径必须登记 owner、关闭日期、观测窗口、关闭阈值和回滚开关。当前计划为：2026-08-31 冻结新增 legacy 能力，2026-09-30 完成 provider 灰度，2026-10-31 达到关闭阈值后停用生产流量，2026-11-30 删除代码；若指标未达标，任务必须记录批准后的新日期，不得静默延期。
- 宪法版本变更必须同步更新 `PROJECT_STATE.yaml`、`WORK_LOG.md` 和受影响任务；审查者需要确认旧客户端和 schema 的兼容窗口。

## 8. 变更和冲突处理

发现用户已有未提交修改时，先在 `WORK_LOG.md` 记录文件和归属，禁止 reset、checkout、清理或覆盖。若改动跨越模块边界，先拆分任务并增加契约测试；若架构决策变化，新增 ADR，不能在实现中悄悄偏离。
