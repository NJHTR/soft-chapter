# RTC-014：Stage + audience 大规模直播分层

## 状态与边界

- 状态：`planned`
- 依赖：RTC-006、RTC-013
- 负责目录：`server` 直播控制面、`deploy/streaming/`、直播前端和 CDN 运维文档
- 禁止修改：让普通观众加入双向 LiveKit 房间；用单一万人 SFU 房间代替分层架构

## 目标

将少量主播/嘉宾放入 LiveKit stage，将普通观众导向 SRS 的 WHEP、LL-HLS/HLS 或 HTTP-FLV/CDN，并支持受控上麦和 breakout room。

## Definition of Done

- [ ] stage 发布者首期不超过 8 人，扩展到 16 人前有压测和 admission。
- [ ] 观众不创建 LiveKit participant，观看链路有独立首帧、延迟和 CDN 命中率指标。
- [ ] 上麦、撤麦、主持人权限、人数和地域限制由控制面校验并可审计。
- [ ] 1 stage + 10k audience 的合成流/真实流验收完成，媒体字节不经过 Spring/Kafka。
