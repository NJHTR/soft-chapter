# RTC-004：1 对 1 LiveKit 媒体适配器

## 状态与边界

- 状态：`in_progress`
- 负责人：`/root`
- 依赖：RTC-002、RTC-003
- 当前波次：C（媒体面）与 D（客户端质量基础）
- 负责目录：`src/modules/rtc/`、`src/api/rtc.ts`、`src/App.vue`、`src/components/Call.vue` 的迁移壳、`server/.../rtc/provider/` 与 `server/.../rtc/webhook/`
- 契约目录：`docs/contracts/rtc-control.openapi.yaml`、`docs/contracts/livekit-webhook.md`
- 禁止修改：群聊 mesh 的生产主路径、直播媒体服务、聊天消息 schema、用户已有的直播/后台改动

## 目标

把 1 对 1 音视频迁移到 LiveKit SFU，形成可替换的 provider-neutral media port：

1. 设备权限、音频优先、视频开关、前后摄像头切换和输出设备处理集中在 adapter。
2. 控制面只通过 REST 创建会话、接受/拒绝、签发短期 token、记录 join/leave；LiveKit SDK 直接完成媒体连接。
3. 远端轨道稳定聚合为 `MediaStream`，页面显式绑定 `srcObject`，重连不会遗留旧元素或旧房间监听器。
4. 官方 LiveKit webhook（Authorization JWT）和迁移期 HMAC header 都能被明确识别，事件账本与状态机保持幂等。
5. 旧 `Call.vue` 仅保留群聊/兼容入口；RTC-004 开启时不得处理 1 对 1 的 offer/answer/ICE。

### 一对一通话 ACL

- direct 通话只要求双方在 `t_follow` 存在双向关注记录（互相关注）。
- 单向关注、未关注均拒绝创建 direct 通话。
- 不要求 `t_friend` 好友记录；群聊仍按 `t_group_member` 成员资格校验。

## 非目标

- 本任务不迁移群聊 roster、simulcast、active speaker 网格；这些属于 RTC-005。
- 本任务不接管直播 WHIP/WHEP、HLS 或 HTTP-FLV；这些属于 RTC-006。
- 本任务不删除旧 WebSocket、WebCodecs 或 C++ stub；退役属于 RTC-009，必须等质量与发布门禁通过。

## 对外契约

- 控制面：`docs/contracts/rtc-control.openapi.yaml`。
- LiveKit webhook：`docs/contracts/livekit-webhook.md`。生产首选官方 `Authorization: Bearer <JWT>`；`LiveKit-Signature: v0=...` 只作为有关闭日期的迁移兼容入口。
- 前端 provider 端口：`src/modules/rtc/adapter/rtcMediaPort.ts`，页面不得直接创建 `RTCPeerConnection` 或调用 LiveKit SDK。
- 通话状态：`RINGING -> ACCEPTED -> NEGOTIATING -> CONNECTED -> ENDING -> ENDED`；终态命令必须可重放且不新增事件。

## 状态、并发与恢复

- `client_request_id` 用于创建幂等；动作请求携带稳定 `event_id` 与 `trace_id`。
- token 只在 `ACCEPTED/NEGOTIATING/CONNECTED` 允许签发，TTL 上限 900 秒；客户端不提交 room 或发布权限。
- 首次加入前显式调用 `join`，provider webhook 再确认 `CONNECTED`；重复 webhook 只写一次 ledger。
- 连接断开进入 `RECONNECTING`，最多 3 次指数退避重连；主动挂断先标记终态再释放 Room，不能被 `Disconnected` 回调重新拉回重连。
- 设备拒绝时保留音频通话；视频采集失败不得阻断 Opus 音频。

## Definition of Done

- [x] 页面新增路径只消费 provider-neutral port；旧直接 P2P 路径被 1 对 1 信令硬隔离。
- [x] `livekit-client` adapter 聚合本地/远端轨道、处理连接状态、设备切换和主动释放。
- [x] REST 动作带稳定 event_id；前端首次 join 与 token、webhook 状态机契约一致。
- [x] OpenAPI、webhook 契约、状态文件、任务索引和工作日志同步。
- [ ] Chrome/Firefox/Safari/移动端真实接通与 TURN relay 验证。
- [ ] 真实 LiveKit webhook → Spring Boot → 状态账本端到端验证。
- [ ] 720p30 正常网与 500 kbps 音频优先 QoE 报告。

## 验证命令

```text
pnpm exec vue-tsc --noEmit --pretty false
pnpm run build-only
mvn -f server/pom.xml test -Dtest=com.douyin.rtc.**
powershell -File deploy/streaming/smoke.ps1 -ProfileName webrtc
```

浏览器双端、TURN/NAT 矩阵和 webhook 端到端命令必须在后端与 LiveKit 容器启动后补录，未完成前不得把任务标记为 `completed`。

## 风险与回滚

- `VITE_LIVEKIT_URL` 未配置时，非 localhost 浏览器必须快速失败并提示部署配置；禁止静默连接 `localhost`。
- 官方 webhook 的 JWT 校验与旧 HMAC 兼容入口必须分别监控；HMAC 入口在迁移窗口结束后由独立任务关闭。
- 回滚只切换 `VITE_RTC_004=off` 并保留旧群聊入口；不得回滚已落库的 RTC 账本或删除 provider webhook ledger。
