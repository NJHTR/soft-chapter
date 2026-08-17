# RTC-005：群聊音视频 SFU 迁移

## 状态与边界

- 状态：`in_progress`
- 依赖：RTC-004
- 负责目录：`src/modules/rtc/` 群组适配、`server/.../rtc/` roster/token、群聊 UI 迁移
- 禁止修改：无限 mesh、客户端自报 group_members、普通聊天 WS 承载媒体

## 目标

首期最多 8 人，使用 LiveKit SFU 的单上行、订阅策略、simulcast/dynacast、active speaker、屏幕共享和成员进出。

## P0 控制面契约

- 群成员列表只从服务端 `t_group_member` 快照读取，创建时包含发起者最多 8 人；
  `rtc_call_participant.profile_snapshot` 保存昵称/头像展示快照，不能反向作为 ACL。
- 群 session 只有第一个接听者将 `RINGING -> ACCEPTED`；后续成员在
  `ACCEPTED/NEGOTIATING` 窗口各自执行 `RINGING -> JOINING`，同一成员重复接听幂等返回。
- 群成员拒绝只将本人置为 `REJECTED`；只有全部被叫成员都拒绝且仍处于振铃时，
  session 才收敛为 `REJECTED`。成员挂断/离开只改变本人；发起者结束或 LiveKit
  房间已无活跃成员时才收敛 session。
- LiveKit `room_started` 只把已经处于 `JOINING/RECONNECTING` 的 roster 成员置为
  `CONNECTED`，不得把尚未接听的 `RINGING/INVITED` 成员提前标记接通。
- 群 token 仍由服务端根据 roster 签发；非发起者必须先进入 `JOINING/CONNECTED/RECONNECTING`，
  未接听成员不能凭群成员身份获取可发布 token。
- 控制面 WebSocket 仅传递来电通知，RTP/SDP/ICE 继续由 LiveKit SDK 完成；旧 mesh
  和 `offer/answer/ice_candidate` 分支不属于本任务主路径。

## 当前 P0 实现

- [x] 后端群 roster 上限、profile snapshot、独立 accept/reject/leave 语义和 token 状态守卫。
- [x] P0 契约单测：8 人上限、多人接听、单成员拒绝、未接听成员不被 connected、
  成员挂断隔离和群 token 权限。
- [ ] 前端群 roster/轨道展示、可见订阅层和屏幕共享。
- [ ] 2/4/8 人真实浏览器 + TURN/SFU 联调。

## DoD

- [ ] 2/4/8 人房间真实浏览器测试通过，参与者互相可见可听。
- [ ] roster 来自服务端群成员快照，成员权限和人数上限服务端校验。
- [ ] 只订阅可见/active speaker 层，弱网先降视频再保音频。
- [ ] 成员加入、离开、拒绝、超时和 provider 故障有幂等事件。
- [ ] 禁止把群聊降级成未监控的多 PeerConnection mesh。

## 验证记录

- `mvn -f server/pom.xml test '-Dtest=com.douyin.rtc.ParticipantTest,com.douyin.rtc.TokenServiceTest'`
  （P0 后端契约测试；提交前需补跑完整 `com.douyin.rtc.**`）。
