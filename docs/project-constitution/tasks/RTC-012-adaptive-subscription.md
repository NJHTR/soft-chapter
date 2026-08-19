# RTC-012：客户端选择性订阅与媒体减载

## 状态与边界

- 状态：`planned`
- 依赖：RTC-005、RTC-007
- 负责目录：`src/modules/rtc/adapter/`、`src/modules/rtc/quality/`、群聊视图和契约测试
- 禁止修改：群聊 mesh、客户端 relay、未验证的 `MediaStream`/`RemoteTrack` 双重绑定

## 目标

补齐 publication 生命周期和元素可见性契约，在不影响现有 1 对 1 画面的前提下实现 simulcast、dynacast、可见 tile/active speaker 订阅、后台视频暂停和音频优先。

## Definition of Done

- [ ] `RtcMediaPort` 能按 identity 控制音频订阅、视频订阅和质量层，不泄漏 LiveKit 类型给业务组件。
- [ ] `adaptiveStream` 仅在真实 `RemoteTrack.attach/detach` 或等价可见性契约通过后打开。
- [ ] 2/4/8 人全视频、只音频、隐藏 tile、最小化、后台 tab、摄像头切换和弱网矩阵通过。
- [ ] 与全量订阅基线相比，群聊下行和 SFU egress 的下降幅度有真实统计和回滚开关。
