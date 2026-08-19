# RTC 选择性订阅策略验收矩阵

本文件对应 `RTC-012` 的基础契约实现。它验证的是 provider-neutral 策略和 LiveKit adapter 的调用边界，不把类型检查当成真实双浏览器媒体验收。

## 策略规则

| 场景 | 可见集合 | 音频 | 视频订阅 | 质量层 |
|---|---|---|---|---|
| 1 对 1 默认 | `null` | 保持订阅 | 保持订阅 | 显式质量或高质量基线 |
| 多人默认 | `null` | 保持订阅 | 保持订阅 | 非发言人 LOW，发言人 MEDIUM |
| 多人显式可见 | 仅当前 tile | 保持订阅 | 仅可见参与者订阅，发言人可例外 | 可见 MEDIUM，发言人 MEDIUM |
| 屏幕共享 | 任意 | 保持订阅 | 保持订阅 | HIGH |
| 显式关闭视频 | 任意 | 按音频设置 | 取消视频订阅 | 不影响音频 |
| 显式关闭音频 | 任意 | 取消音频订阅 | 不改变视频策略 | 不影响视频 |

## 必须验证的生命周期

1. `TrackSubscribed` 后建立参与者 MediaStream，应用当前订阅策略。
2. `TrackUnsubscribed` 后移除对应轨道；仍有其他轨道时不得删除整个参与者流。
3. 摄像头关闭和重新打开只能替换同一种媒体轨道，不能让旧的 ended track 覆盖新轨道。
4. 参与者离开房间后清理质量和订阅覆盖设置。
5. `setVisibleParticipants(null)` 恢复兼容的全量订阅模式。
6. 离开或销毁房间后清空所有策略状态，下一次入房不能继承旧房间设置。

## 验证命令

```text
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc/adapter/livekitAdapter.ts src/modules/rtc/adapter/rtcMediaPort.ts src/modules/rtc/quality/subscriptionPolicy.ts
pnpm run build-only
git diff --check
```

真实验收仍需两个或更多已登录浏览器执行 1 对 1、4 人和 8 人房间，并记录浏览器 WebRTC stats、LiveKit egress、首帧时间、视频订阅数和音频连续性。完成这组验收前，`adaptiveStream` 保持关闭，RTC-012 不得标记为 `completed`。
