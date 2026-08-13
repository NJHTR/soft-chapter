# RTC-004：1 对 1 LiveKit 媒体适配器

## 状态与边界

- 状态：`planned`
- 依赖：RTC-002、RTC-003
- 负责目录：`src/modules/rtc/`、`src/components/Call.vue` 迁移壳、`server/.../rtc/provider/`
- 禁止修改：群聊 mesh 作为主路径、直播媒体服务、聊天消息 schema

## 目标

把 1 对 1 音视频迁移到 LiveKit：设备管理、H.264/VP8 + Opus 协商、音频优先、静音/摄像头/设备切换、接通/重连、TURN 和通话记录。

## DoD

- [ ] 页面不直接散落 `new RTCPeerConnection`，只调用 provider-neutral port。
- [ ] Chrome/Firefox/Safari/移动端真实接通；TURN relay 可验证。
- [ ] 设备权限拒绝、后台、ICE restart、重复 hangup 和 provider webhook 都可收敛。
- [ ] 720p30 正常网 QoE 达标，弱网 500 kbps 保持 Opus 音频。
- [ ] legacy Call.vue 仅作兼容入口，迁移指标和回滚开关可见。

