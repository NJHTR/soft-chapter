# ADR-001：LiveKit 作为通话和互动连麦 SFU

- 状态：accepted
- 日期：2026-08-13

## 背景

当前 1 对 1 和群聊使用浏览器 mesh，缺少 TURN、稳定的成员状态、订阅策略和群规模边界。自行实现 libwebrtc/RTP/SFU 的成本和风险高，现有 C++ WebRTC 代码也只是 stub。

## 决策

LiveKit 作为 1 对 1、群聊和直播互动连麦的生产媒体 provider。Spring Boot 负责 token、房间权限、业务状态、webhook 和审计；前端通过 provider adapter 使用 SDK。群聊首期最多 8 人，启用 simulcast/dynacast/active speaker。

## 备选方案

- mediasoup：灵活但需要维护更多 SFU 控制面和客户端适配。
- Janus：插件化成熟，但业务房间和 token 集成需要更多自建。
- 继续 mesh：仅适合小型原型，不能满足群聊和弱网要求。
- MCU：录制/合屏有价值，但常规互动延迟和成本过高。

## 后果

需要部署 LiveKit、签发短 token、验证 webhook 和增加 provider 监控；换来的收益是统一的 ICE、NACK/RTX/TWCC、重连、订阅和录制接口。不得让业务代码直接依赖 LiveKit 类型，保留 `RtcMediaPort` 以便未来替换。
