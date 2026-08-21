# RTC-CALL-001: 高并发呼叫生命周期、离线恢复与可靠超时

## 状态与边界

- 状态：`in_progress`（控制面第一批实现完成，真实多实例/故障/负载门禁未完成）
- 负责人：`/root`
- 分支/开始时间：`dev/full` / 2026-08-21
- 依赖：RTC-003、RTC-004、RTC-007、RTC-011
- 负责目录：`server/.../rtc`、RTC 数据迁移、call-specific outbox、控制面契约与 runbook
- 禁止修改：媒体字节进入 Spring/Kafka/WebSocket；把 Redis 或 WebSocket 作为 CallSession 真相；
  用 MySQL 秒级扫描实现超时；把旧 `Call.vue` 信令当生产主路径

## 权威模型

- MySQL `rtc_call_session` 是 durable fact；Redis ZSET 是可重建 timeout index；Kafka/outbox 是事件
  交付；WebSocket 是低延迟通知。
- 客户端以 `state_version` 比较新旧状态，以 `server_now/ring_at/expires_at` 显示剩余时间。
  `Date.now()` 不参与最终状态判定。
- `RINGING` 的 `ACCEPT/REJECT/CANCEL/EXPIRE` 使用 `state + state_version` 单语句 CAS。第一个
  `affectedRows=1` 的命令获胜，其余命令重读权威终态。
- 业务边界：服务端在 `expires_at` 之前收到并成功 CAS 的 ACCEPT 有效；超时 worker 先 CAS 成功后，
  任意晚到 ACCEPT 返回 `EXPIRED`。不接受客户端自报点击时间覆盖服务端事实。
- `event_id` 去重；`event_version` 携带生成事件时的 `state_version`。同一 call 的 Kafka key 固定
  `callId`，消费者仍须拒绝旧版本。

## 已实现

1. migration 042：`ring_at/state_version/event_version` 与 reconciliation/ending/event-version 索引。
2. `CallService` 事务边界：会话 CAS、参与者、事件账本和启用 Kafka 时的 call outbox 同事务提交。
3. `GET /api/rtc/calls/active`：登录/重连主动查询有效会话；过期 RINGING 不复活。
4. `/ws/chat` 鉴权建连后向新设备推送 `rtc.call.reconciliation`；已提交事件向所有参与者设备广播
   `rtc.call.state`，客户端按版本幂等收敛。
5. 32 分片 Redis ZSET（可配置）按 score=`expireAt` 调度。steady-state worker 不扫描 MySQL；仅启动
   或检测到 Redis 故障恢复时分批重建索引。重复获取由 MySQL CAS 去重。
6. `rtc-call-events` transactional outbox，key=`callId`，包含 `event_id/event_version/state_version`。
   Kafka 不可用时积压，不改变已建立媒体会话。
7. Call ID 格式校验、20~40 秒 jitter negative cache 与 JVM single-flight；不存在 ID 重复请求不持续
   回源 MySQL。目标账号存在性独立校验，已存在但离线仍可创建 RINGING 会话。
8. 低基数状态迁移/冲突、幂等命中、reconciliation 结果和 timeout lag Micrometer 指标。

## 仍未实现/未验证

- Presence 目前仅有节点内多 WebSocket 事实；Redis 多实例 OFFLINE/CONNECTING/ONLINE/AWAY/BUSY、
  heartbeat lease、旧连接 fencing 尚未实现。
- 用户不存在已由 ACL 参数/关系阻断，但 `USER_NOT_FOUND/USER_BLOCKED/USER_OFFLINE` 的完整公开错误
  契约、账号删除/拉黑中途重校验尚未完成。
- 正缓存/热点 CallSession 分片与 Bloom filter 尚未实现；当前只对不存在 ID 做 negative cache 和
  single-flight，权威详情仍读 MySQL 以避免陈旧状态。
- Hikari bulkhead/circuit breaker、EXPLAIN 实测、Kafka call consumer retry/DLQ 回放、DLQ 管理接口未完成。
- Redis Cluster failover、Kafka broker outage、MySQL slow/down/lock、WebSocket 丢包与跨节点广播尚未做
  真实环境注入；这里只能称为代码和单元测试证据。

## 验证与 DoD

```text
mvn -f server/pom.xml -Dtest=com.douyin.rtc.** test
mvn -f server/pom.xml -Dtest=com.douyin.kafka.** test
mvn -f server/pom.xml test
git diff --check
```

- [x] 状态 + 版本 CAS、服务端时间、边界先后顺序单测
- [x] login/reconnect reconciliation 与多设备广播代码/单测
- [x] Redis ZSET steady-state timeout、启动/恢复重建代码/单测
- [x] call-specific transactional outbox 代码/单测
- [ ] T-5s/T-1s/T-100ms/T+100ms/T+1s 真实网络延迟矩阵
- [ ] Redis/Kafka/MySQL/WebSocket 多实例故障注入
- [ ] 10k/100k/1m pending call 分片容量与 timeout lag 报告
- [ ] Hikari/慢查询/锁等待门禁与 EXPLAIN 证据
- [ ] 浏览器离线上线、多端接听、丢消息重连验收

## 回滚

- 部署 migration 042 后字段向后兼容；旧实例忽略新增列。回滚应用时保留列和索引，不做破坏性 DDL。
- `rtc.call.timeout-poll-ms`/`timeout-shards` 只影响新 worker；Redis 故障时停止超时推进并在恢复后重建，
  禁止临时切回秒级全表扫描。
- `DOUYIN_KAFKA_ENABLED=false` 停止新 Kafka/outbox 发布；RTC MySQL 事件账本和 reconciliation 仍工作。
- WebSocket 广播异常不回滚已提交状态；客户端通过 `GET /api/rtc/calls/active` 恢复。
