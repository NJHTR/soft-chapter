# RTC 通话流程验收记录

**日期：** 2026-08-19  
**范围：** LiveKit 1 对 1 音视频控制面、前端通话面板、忙线并发语义

## 已验证的自动化契约

| 场景 | 结果 | 验证方式 |
|---|---|---|
| A 呼叫 B，B 接听并进入 CONNECTED | 通过 | RTC 全量 JUnit 契约测试 |
| A/B 挂断后参与者释放、会话进入终态 | 通过 | `ParticipantTest`、`CallTtlWorkerTest` |
| 主叫回铃音、被叫来电铃声与震动 | 代码检查通过 | `startCallRingtone` 生命周期绑定 |
| 30 秒无人接听自动结束 | 代码检查通过 | 前端计时器与服务端 `rtc.call.ringing-ttl` 对齐 |
| 同一 `client_request_id` 重试不创建第二通 | 通过 | `IdempotencyTest` |
| 已接通的 A 再呼叫 C | 通过 | 返回 `BUSY`/409，不创建新会话 |
| C 呼叫正在与 B 通话的 A | 通过 | 目标忙线守卫；竞态时 `call_busy` 信令结束 C 的拨号 |
| 群通话中已离开的成员再次接听 | 通过 | 参与者状态过滤测试 |
| 前端主画面/右上角小窗切换 | 代码检查通过 | 主画面不可点击，只切换当前右上角小窗 |
| 最小化、恢复、摄像头开关同步 | 代码检查通过 | LiveKit 轨道与 Vue 状态绑定逻辑检查 |

## 业务约定

1. A 与 B 通话期间，C 呼叫 A 不会覆盖 A-B 的通话页面，也不会创建第二个 LiveKit 房间。
2. 正常路径在服务端 `POST /api/rtc/call` 阶段返回 `BUSY`；若发生“先建呼、后进入通话”的竞态，A 会通过 WebSocket 返回 `call_busy`，C 取消自己的未接通会话。
3. A-B 任意一方挂断后，前端立即退出通话面板，媒体连接释放；服务端参与者状态和会话状态通过轮询/webhook 收敛。
4. 群通话只对仍处于 `INVITED/RINGING/JOINING/CONNECTED/RECONNECTING` 的成员判忙，已经 `LEFT/REJECTED/CANCELLED` 的成员不再占用通话能力。

## 仍需浏览器现场确认

当前会话未附着 A、B、C 三个登录浏览器，因此以下项目不能仅凭构建结果宣称完成：

- 两个真实浏览器完成 A→B 接听、互点右上角切换、最小化/恢复、挂断；
- A 拨号时主叫听到回铃音，B 收到持续来电铃声和震动；接听后两端提示都停止；
- A 取消未接通电话后，B 的来电提示应立即停止；双方按服务端 `expires_at`（默认 30 秒）自动退出；
- 关闭/重新打开摄像头后，另一端头像与画面是否逐次同步；
- A-B 通话中 C 呼叫 A 时，C 端是否收到“对方正在通话中”提示且 A-B 画面不闪断；
- 设备权限拒绝、刷新页面、后台切换和 LiveKit 重连。

现场验收时应在浏览器控制台记录 `[rtc]`、`[WS]` 日志，并检查 LiveKit 房间内每种媒体每个参与者只有一条有效轨道。

## 验证命令

```text
mvn -q -f server/pom.xml -Dtest=com.douyin.rtc.** test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc/store/useRtcStore.ts src/modules/rtc/signaling/wsBridge.ts
pnpm run build-only
git diff --check
```
