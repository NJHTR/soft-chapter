# ADR-002：SRS 负责直播 ingest 与分发

- 状态：accepted
- 日期：2026-08-13

## 决策

直播使用 SRS 作为单一 ingest/distribution 主路径：主播优先 WHIP，兼容 SRT/RTMP；观众优先 WHEP/WebRTC，按网络和规模 fallback 到 LL-HLS/HLS/HTTP-FLV。SRS 不承载通话房间。

MediaMTX 只能作为明确的替代部署 profile，不能与 SRS 共享同一默认端口或职责。LiveKit 的 Egress/DVR 和 SRS 的录制能力按场景二选一，避免双重转码。
