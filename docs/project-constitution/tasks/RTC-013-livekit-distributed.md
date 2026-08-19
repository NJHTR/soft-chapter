# RTC-013：LiveKit 多节点、Redis 路由与 TURN 区域池

## 状态与边界

- 状态：`planned`
- 依赖：RTC-011
- 负责目录：`deploy/streaming/`、`deploy/rtc/`、运维配置和部署文档
- 禁止修改：让 Spring 或 Kafka 转发媒体；在没有 room placement 的情况下水平复制 LiveKit

## 目标

使用 Redis 共享路由和房间放置，将独立通话房间分配到多个 LiveKit 节点，并建立多地域 coturn 池、节点 drain 和故障恢复策略。

## Definition of Done

- [ ] 新房间不会落到健康阈值以上的节点，已有房间的 drain/restart 行为有明确策略。
- [ ] Redis 延迟、Redis 故障、LiveKit 节点故障和 TURN 区域切换有故障注入记录。
- [ ] 100/1000 并发房间压测无跨房串流，连接 SLO 相对单节点基线不劣化超过 20%。
