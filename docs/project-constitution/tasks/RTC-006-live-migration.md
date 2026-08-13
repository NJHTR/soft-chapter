# RTC-006：直播 WHIP/WHEP 迁移

## 状态与边界

- 状态：`planned`
- 依赖：RTC-002、RTC-003
- 负责目录：`src/pages/live/`、`src/modules/live/`、`server/.../live/`、SRS 配置和直播契约
- 禁止修改：把 `/ws/live` 二进制/文本继续当生产媒体总线、继续维护 SDP echo、引入第二套默认媒体服务

## 目标

主播使用 WHIP（SRT/RTMP 兼容），观众优先 WHEP/WebRTC，按规模和浏览器 fallback 到 LL-HLS/HLS/HTTP-FLV；聊天/点赞/presence 单独走控制 WS。

## 必查兼容项

- `src/utils/streaming/webcodecs_sender.ts` 和 player 当前存在 `/api/live/webrtc/offer` 路径，与控制面 `/api/live/engine/webrtc/offer` 不一致；迁移必须删除伪造 offer 路径或明确返回 `provider_not_ready`，不能留下第二条 API。
- legacy WebCodecs `copyTo()`、`getVideoTracks()`、codec await 和空 canvas 缺陷只做风险记录，不能成为新生产链路。

## DoD

- [ ] 真实 WHIP ingest、WHEP 播放、主播重连和 HLS/HTTP-FLV fallback 通过。
- [ ] `/ws/live` 只负责控制，媒体不进入 Spring/Kafka/chat WS。
- [ ] viewer presence 以 `(room,user,session)` 幂等，数据库和连接数不双计。
- [ ] 主播所有权、stream key、play token、房间状态和 viewer 权限有契约测试。
- [ ] 2026-09-30 前完成灰度指标采集，按路线图达成 legacy 退役门槛。

