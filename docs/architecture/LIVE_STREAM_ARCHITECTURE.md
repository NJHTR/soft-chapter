# 直播媒体架构

## 1. 正式媒体链路

```text
主播浏览器/OBS
       |
       | WHIP（首选）/SRT 或 RTMP（兼容）
       v
     SRS ingest
       |
       +--> WHEP/WebRTC：互动、低延迟、小规模
       +--> LL-HLS/HLS/HTTP-FLV：大规模、CDN、兼容浏览器
       +--> recording provider（按场景选择唯一实现）：异步录制和 VOD
```

Spring Boot 只签发短期 ingest/play token、绑定 `room_id`、校验主播所有权、接收 SRS 状态和维护单一 viewer presence。媒体帧不经过 `LiveStreamHandler`、Kafka 或普通聊天 WS。SRS ingest 使用受控的 stream key/HTTP callback 或 provider JWT（由部署 profile 明确一种方式）；WHEP 播放使用短期播放 token/签名 URL；具体 endpoint、TTL、签名算法和失败码必须在 `contracts/rtc-control.openapi.yaml` 与部署配置中固定。

## 2. 迁移期通道

旧 `/ws/live/{room}` 只允许作为 `legacy-bridge`：

- `/control`：文本 JSON，聊天、点赞、房间状态；必须有 envelope、长度限制、权限、序号和频率限制。
- `/media`：迁移期只读或受 feature flag 保护，不再新增自定义 WebCodecs 帧能力。
- 正式新功能全部使用 WHIP/WHEP 或媒体服务 SDK。

退役前的 feature flag 必须能按房间、客户端版本和百分比关闭 legacy；bridge 需要监控连接数、失败率、画质和最后使用版本。生产控制 WS 的 Origin 必须来自 allowlist，未知来源在握手阶段拒绝。

## 3. 质量档位

初始直播 ladder 依据真实编码器能力选择：

| 档位 | 分辨率 | 帧率 | 目标视频码率 | 适用 |
|---|---:|---:|---:|---|
| high | 1920x1080 | 30/60 | 4-8 Mbps | 稳定上行、互动/大屏 |
| medium | 1280x720 | 30/60 | 2.5-4 Mbps | 默认观看 |
| low | 960x540 | 30 | 1.2-2 Mbps | 中等网络 |
| fallback | 640x360 | 30 | 0.6-1 Mbps | 弱网/移动端 |

每个档位必须有真实编码、关键帧间隔 1-2 秒、音频 Opus/AAC 配置、可观测切换原因。不能把 480p 超分后标成 1080p。

## 4. 直播房间状态

`CREATED -> STARTING -> LIVE -> DEGRADED -> ENDING -> ENDED`。只有 ingest 健康、媒体服务返回可用状态并完成主播授权后，数据库房间才能进入 `LIVE`。服务重启不能无条件把所有 LIVE 标成 ENDED；应通过 provider health/reconciliation 恢复或进入 `DEGRADED`。

viewer presence 由一个服务权威维护，以 `(room_id, user_id, session_id)` 幂等；数据库 join/leave 计数与 WebSocket 连接数不得双重增减。

## 5. 现有硬故障迁移顺序

1. 停止把 WebCodecs 自定义二进制当作直播生产链路。
2. 补齐 SRS 配置、健康检查、WHIP/WHEP smoke test 和 token 校验。
3. 将 LiveCreate 改为一次 ingest 连接，LiveWatch 改为 WHEP/LL-HLS/HLS fallback。
4. 保留聊天/点赞控制 WS，但与媒体连接分开。
5. 在 provider 路径有真实 QoE 和回滚后，再退役 legacy。
