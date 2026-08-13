# RTC-008：录制、转码和对象存储

## 状态与边界

- 状态：`planned`
- 依赖：RTC-005、RTC-006
- 负责目录：`server/.../recording/`、异步 FFmpeg worker、对象存储配置和审核 metadata
- 禁止修改：为互动通话引入实时 MCU、双 provider 同时录制、无同意录制

## 目标

通话/群聊使用 LiveKit Egress 独立轨道；直播使用 SRS DVR 源流；合屏、ABR、封面和 VOD 由异步 FFmpeg worker 派生。

## DoD

- [ ] recording_id/provider 唯一约束和 webhook 幂等生效。
- [ ] 同意、轨道、保留期、删除审计和访问权限可查询。
- [ ] 断点、重试、音画同步、对象存储写入和失败升级有测试。
- [ ] 同一房间不会重复录制或重复转码。

