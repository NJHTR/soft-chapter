# RTC 总体架构

## 1. 目标拓扑

```text
                         +----------------------+
| Spring Boot 控制面   |
                         | Auth / ACL / Room    |
                         | Token / Call ledger  |
                         | Webhook / QoE index  |
                         +----------+-----------+
                                    |
                         HTTPS + versioned WS
                                    |
       +----------------------------+----------------------------+
       |                                                         |
       v                                                         v
+--------------+      WebRTC media      +----------------+   +---------+
| Web client   | <--------------------> | LiveKit SFU   |   | coturn  |
| RTC adapter  |                         | calls/rooms   |   | TURN    |
+--------------+                         +----------------+   +---------+
       |                                          |
       | live publish/play                        | Egress/webhook
       v                                          v
+--------------+   WHIP/SRT/RTMP   +----------------+  +-------------+
| Live client  | ----------------> | SRS            |  | Recorder /  |
| broadcaster  |                   | ingest + edge  |->| FFmpeg job  |
+--------------+                   +--------+-------+  +-------------+
                                           |
                              WHEP/WebRTC  |  HLS/HTTP-FLV
                                           v
                                      viewers/CDN
```

LiveKit 只承载通话、群聊和互动连麦；SRS 只承载直播 ingest、协议转换和大规模观看出口。两者共享身份、房间授权、指标和审计标准，但不共享媒体轨道或端口。

## 2. 分层职责

### 控制面

Spring Boot `rtc-domain` 负责编排；`rtc-persistence` 负责权威账本写入：

- 创建/接受/拒绝/结束 CallSession；
- 从用户和群服务生成成员快照并校验角色；
- 签发短期 LiveKit/SRS/coturn token；
- 接收 provider webhook，幂等调用 `rtc-persistence` 更新 call ledger；
- 推送版本化控制事件和聊天投影；
- 记录请求、延迟、质量摘要和审核审计。

控制面不做 SDP 解析、不做 RTP 转发、不把媒体帧塞进 Kafka 或聊天 WebSocket。`chat-persistence` 只保存 CallEvent 的兼容消息投影。

### 媒体面

- LiveKit：SFU、ICE/DTLS-SRTP、NACK/RTX/TWCC、订阅、simulcast、active speaker、重连。
- coturn：UDP/TCP/TLS 443 relay，使用短期 REST 凭据。
- SRS：主播 WHIP/SRT/RTMP ingest，观众 WHEP WebRTC 和 HLS/HTTP-FLV fallback。
- Egress/DVR/FFmpeg：异步录制、转码、缩略图和对象存储。

### 客户端

业务页面只依赖 `RtcMediaPort`、`LivePublisherPort` 和 `LivePlayerPort`。provider adapter 负责 SDK 细节；设备管理、质量策略、状态 store、参与者网格和控制按钮不直接构造底层 PeerConnection。

## 3. 现有代码到目标模块的映射

| 当前位置 | 目标归属 | 迁移方式 |
|---|---|---|
| `src/components/Call.vue` | `rtc-frontend` | 拆为 store、signaling、media adapter、grid、controls；保留壳作为兼容入口 |
| `src/utils/socket.ts` | `rtc-signaling` | 保留聊天传输，新增 envelope adapter；媒体不复用该通道 |
| `ChatWebSocketHandler` | `rtc-signaling` + `chat-persistence` | 只路由控制事件，加入 schema/ACL/idempotency |
| `GroupChatController/Service` | `chat-persistence` + roster port | 成员快照供 RTC 使用，补 membership/role 校验 |
| `StreamController/LiveController` | `live-domain` | token、room、ingest/play URL；删除 echo SDP 语义 |
| `LiveStreamHandler` | `legacy-bridge` | 只保留聊天/点赞兼容，设置退役日期，禁止媒体帧 |
| `src/utils/streaming/*` | `legacy-bridge` 或 `media-quality` | WebCodecs 仅用于本地处理/录制，生产推流改 WHIP/WebRTC |
| `streaming-engine/network/webrtc` | `native-engine` | 暂停生产依赖；后续必须接入真实库并独立验证 |
| `server/.../engine` | `native-engine` | JNI 可选，native unavailable 安全回退 |

## 4. 关键生命周期

1. 客户端向控制面申请 CallSession。
2. 控制面校验目标用户/群成员、权限、人数和幂等键，写入 `RINGING`。
3. 被叫通过控制面事件接受；控制面签发 provider token 并广播 roster。
4. 客户端进入 LiveKit room，provider 完成真实 ICE/媒体协商。
5. provider webhook 和客户端 QoE 事件更新连接状态；断线进入 `RECONNECTING`。
6. 所有参与者离开或主动结束后，控制面写入 `ENDED`，聊天消息只作为展示投影。

直播则由主播获取 ingest token 后一次推流到 SRS；观看者先用 WHEP/WebRTC，失败或规模策略触发 LL-HLS/HLS/HTTP-FLV fallback。

## 5. 容量边界

- 1 对 1：音频/视频双向，默认 720p30，支持 1080p30 的能力协商。
- 群聊首期：最多 8 人；发布端只一条上行，订阅由 SFU 根据视图和 active speaker 控制。
- 互动连麦：房主和嘉宾使用 LiveKit；普通观看者走直播分发链路，不为每个观众创建 P2P 连接。
- 大规模直播：WebRTC 只服务互动和低延迟小规模观看，CDN/HLS 承接长尾观众。
