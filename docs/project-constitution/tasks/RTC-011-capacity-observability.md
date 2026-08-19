# RTC-011：SFU/TURN/客户端容量观测、Admission 与压测

## 状态与边界

- 状态：`planned`
- 依赖：RTC-007
- 负责目录：`deploy/streaming/`、监控配置、`src/modules/rtc/quality/`、压测工具和 `docs/runbooks/`
- 禁止修改：把原始 RTP/高频 stats 写入 Kafka 或业务聊天 WS；用单机开发数据宣称生产容量

## 目标

建立 LiveKit、coturn、客户端和控制面的统一容量基线，按 CPU、内存、Mbps、pps、连接数、轨道数和 relay bytes 做 room admission。

## Definition of Done

- [ ] 客户端每 2-5 秒聚合 QoE，服务端能关联 `trace_id/room_id/call_id`。
- [ ] Prometheus 只使用低基数标签，room/user/call 明细写入聚合存储。
- [ ] 完成 100×2、100×8、1000×2 房间和 stage + audience 压测矩阵。
- [ ] 节点连续 5 分钟超过 70% 告警，任一关键资源超过 80% 阻止新房间，保留 30% headroom。
