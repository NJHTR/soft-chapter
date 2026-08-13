# ADR-004：H.264/VP8 + Opus 为浏览器基线

- 状态：accepted
- 日期：2026-08-13

浏览器通话视频默认协商 H.264 或 VP8，音频使用 Opus 48 kHz。AV1 仅在 `isConfigSupported` 和 provider 能力都确认时启用；H.265 不作为 WebRTC 生产基线。直播兼容出口必须能提供 H.264 + AAC/Opus。

该决策优先解决跨浏览器、移动设备和硬件编码可用性，不以 AV1/H.265 的理论压缩率换取首帧失败或黑屏。
