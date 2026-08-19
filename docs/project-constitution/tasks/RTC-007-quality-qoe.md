# RTC-007：媒体质量、QoE、自适应和弱网恢复

## 状态与边界

- 状态：`planned`
- 依赖：RTC-004、RTC-005、RTC-006
- 负责目录：`src/modules/rtc/quality/`、`src/modules/rtc/adapter/`、`src/modules/rtc/store/`、`src/modules/live/quality/`、`server/.../metrics/`、监控配置
- 禁止修改：用超分/滤镜掩盖源质量、将指标写成假常量、绕过 provider 拆自制拥塞控制

## 容量与客户端减载边界

- 控制面、SFU、TURN、直播 CDN 分开测量；Spring/Kafka/聊天 WS 不承载媒体帧。
- 群聊使用 SFU；先完成 publication 生命周期、可见性和 `<video>` attach/detach 契约，再启用
  `adaptiveStream`、选择性订阅和 active-speaker 层切换。
- `dynacast`/simulcast 可以让客户端停止无订阅层，但不能把参与者变成群聊 relay。
- 1 对 1 P2P 只能作为后续显式 feature flag 的实验，不能作为本任务的默认拓扑或群聊回退。

## 目标

实现能力协商、simulcast/ABR、QoE 事件、音频优先、ICE restart、降层/降分辨率/降帧率和恢复滞后策略。

## 前置实现证据（不代表任务已认领或完成）

- `7859fd7`：增加 provider-neutral QoE snapshot，并在通话期间读取远端 inbound RTP 统计。
- `94370b5`：按 `trackSid/source` 区分摄像头、屏幕共享和音频轨道；采样串行化、停止后不回写，并在 LiveKit 信令重连后恢复。
- 当前快照只保存在客户端 store，尚未关联完整 room/call/trace，也不驱动 ABR；RTC-006 完成前本任务保持 `planned`。

## DoD

- [ ] 指标 schema 和 2-5 秒采样窗口生效，关联 room/call/trace。
- [ ] RTT/jitter/loss/available bitrate 驱动质量策略，重复切换有滞后。
- [ ] 正常网、移动网、企业网 TURN、极端弱网矩阵通过。
- [ ] 接通、首帧、冻结率、RTT、TURN 成功率和恢复率达到文档 SLO。
- [ ] QoE dashboard 能区分 provider、浏览器、设备、地域和版本。

## 后续拆分任务

- `RTC-011`：SFU/TURN/客户端 QoE 基线、容量公式、room admission 和压测矩阵。
- `RTC-012`：publication 生命周期、可见订阅、simulcast/dynacast/adaptiveStream、音频优先。
- `RTC-013`：Redis 共享路由、LiveKit 多节点/多地域、room placement 和 TURN 区域池。
- `RTC-014`：LiveKit stage + SRS/CDN audience、上麦和 breakout room。
- `RTC-015`：受控 1 对 1 P2P 实验、ICE 质量探测和 SFU 回退。
