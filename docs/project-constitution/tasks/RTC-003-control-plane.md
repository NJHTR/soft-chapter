# RTC-003：RTC 控制面和通话领域

## 状态与边界

- 状态：`planned`
- 依赖：RTC-001
- 可与 RTC-002 并行；provider smoke 使用 CLI 或测试密钥
- 负责目录：`server/.../rtc/`、`server/sql/`、`server/.../websocket/` 的 RTC adapter、`docs/contracts/`
- 禁止修改：媒体服务实现、LiveKit SDK 内部信令、C++ WebRTC stub

## 目标

实现 CallSession/Participant/CallEvent 权威状态机、群成员 ACL、幂等命令、TTL worker、LiveKit token API、webhook ledger 和旧消息投影。`rtc-persistence` 写真相，`chat-persistence` 只投影。

## DoD

- [ ] 完整 transition table 和错误码有契约测试。
- [ ] `event_id`、`client_request_id`、`call_id + user_id` 唯一约束生效。
- [ ] 未登录、非成员、过期、重复、乱序命令被拒绝或安全返回当前状态。
- [ ] token TTL 60-900 秒，发布/订阅权限来自服务端，不信任客户端 roster。
- [ ] LiveKit webhook 签名校验、event id 去重和重放测试通过。
- [ ] 旧 `msg_type=10/11` 记录保留兼容字段并带 `call_id` 投影。

