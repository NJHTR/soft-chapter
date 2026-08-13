# RTC-005：群聊音视频 SFU 迁移

## 状态与边界

- 状态：`planned`
- 依赖：RTC-004
- 负责目录：`src/modules/rtc/` 群组适配、`server/.../rtc/` roster/token、群聊 UI 迁移
- 禁止修改：无限 mesh、客户端自报 group_members、普通聊天 WS 承载媒体

## 目标

首期最多 8 人，使用 LiveKit SFU 的单上行、订阅策略、simulcast/dynacast、active speaker、屏幕共享和成员进出。

## DoD

- [ ] 2/4/8 人房间真实浏览器测试通过，参与者互相可见可听。
- [ ] roster 来自服务端群成员快照，成员权限和人数上限服务端校验。
- [ ] 只订阅可见/active speaker 层，弱网先降视频再保音频。
- [ ] 成员加入、离开、拒绝、超时和 provider 故障有幂等事件。
- [ ] 禁止把群聊降级成未监控的多 PeerConnection mesh。

