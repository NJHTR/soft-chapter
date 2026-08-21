# Kafka 可靠性契约（outbox / 重试 / DLQ / 消费幂等）

> 归属：chat-persistence（outbox）与 rtc-persistence / 控制面事件管道共用的事件底座。
> 目标（总任务 §14/§15/§18~§22/§43）：Kafka 只承载**控制事件**，不承载媒体字节（宪法 §2.1）。

## 1. 职责划分

| 层 | 职责 | 实现 |
|---|---|---|
| 生产者 | 启用 outbox 时先写 `event_outbox`（PENDING），由调度器至少一次投递 | `KafkaMessagePublisher` + `EventOutboxDispatcher` |
| 消费者 | 处理前查 `kafka_event_ledger` 去重，成功后记账再 ack | `KafkaMessageConsumer` / `VideoEventConsumer` / `CoverExtractConsumer` |
| 重试/DLQ | 消费失败指数退避重投，超限进 `<topic>-dlq` | `kafkaListenerContainerFactory`（`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`） |
| 观测 | outbox 存量、重试/死信/去重计数、consumer lag gauge | `KafkaReliabilityMetrics` + `KafkaLagProbe`（Micrometer） |

## 2. 数据契约

### event_outbox（migration_040）

```text
id, topic, event_key, event_id(UNIQUE(topic,event_id)), payload(JSON), status,
retry_count, next_attempt_at, created_at, updated_at, sent_at
status: PENDING -> PROCESSING -> SENT | DEAD
       PROCESSING 超时(outbox-stale-processing-ms)后由 recoverStale 回到 PENDING
```

- 只写控制事件 JSON；禁止媒体帧/RTP/base64（宪法 §2.1/§38）。
- 调度器同步 `send().get(5s)`，投递成功才 `SENT`；失败按 `base*2^retry` 退避，
  超过 `outbox-max-attempts` 置 `DEAD`（人工/告警恢复）。
- 多实例安全：`tryClaim` 为 `PENDING→PROCESSING` 的 CAS，同一行只有一个实例投递。
- 至少一次交付：Kafka 投递与 outbox `SENT` 记账不原子，因此允许重复投递。
- outbox 写入失败会向调用方抛错，禁止降级直发。只有调用方自身已处于业务数据库事务中时，
  业务状态与 outbox 行才具备原子提交语义；脱离业务事务的调用只能保证事件已持久入队，不能宣称
  “业务状态 + outbox”原子一致。

### kafka_event_ledger（migration_041）

```text
PK(topic, event_id), processed_at
```

- 消费者时序：`isProcessed`（处理前）→ 处理 → `markProcessedOrThrow`（成功后 INSERT IGNORE）→ ack。
- chat/group 的数据库副作用与 strict ledger 写入共用事务，WebSocket 推送注册为 after-commit；
  外部副作用消费者仍只有 at-least-once，不能据此宣称 exactly-once。
- 处理失败不记账 → 重试/DLQ 不被账本阻断；记账后 ack 前崩溃 → 重投时查账跳过，避免重复副作用。
- `eventId` 为 null 的旧格式事件不做去重（兼容历史 producer）。
- 保留期 `ledger-retention-hours`；处理前查询 DB 不可用时 fail-open（放行 + `kafka.ledger.failopen` 指标），
  但处理后的 strict 记账失败必须禁止 ack 并进入重试/DLQ。
  账本降低重复概率，但无法让外部副作用（WebSocket、FFmpeg、对象存储）获得 exactly-once 语义；
  此类消费者仍必须让业务副作用本身具备幂等键。

## 3. 事件 JSON 必选/可选字段

- DTO 新增可选 `event_id`（向后兼容，宪法 §2.4）。
- 生产者发布时 `eventId` 为空自动生成 UUID。
- 经 `KafkaMessagePublisher` 的事件：chat/group/notification/video 均带 `event_id`。
- `CoverExtractEvent.of` 也生成 `event_id`，因此现有直发路径同样进入消费账本去重。
- outbox 调度器按 topic 恢复 DTO 类型后发送，保留 Spring Kafka JsonSerializer 的类型头；未知 topic 才使用 JSON 树并由专用消费者处理。

## 4. 指标（Micrometer，低基数）

| 指标 | 含义 |
|---|---|
| `kafka.outbox.pending` | PENDING 存量的 gauge |
| `kafka.outbox.dispatched{topic}` | 投递成功计数 |
| `kafka.outbox.retry{topic}` | 失败重排计数 |
| `kafka.outbox.dead{topic}` | 超过最大尝试置 DEAD 计数 |
| `kafka.outbox.enqueue.failure{topic}` | outbox 写入失败且发布失败计数 |
| `kafka.ledger.duplicate{topic}` | 消费去重命中计数 |
| `kafka.ledger.failopen{topic}` | 账本 DB 异常 fail-open 计数 |
| `kafka.dlq{topic}` | 重试耗尽进 DLQ 计数 |
| `kafka.consumer.lag{group,topic,partition}` | 消费 lag gauge（30s 采样） |

RTC call 事件使用独立 `rtc-call-events` topic，`event_key=callId`，使同一 aggregate 固定进入同一
partition；payload 同时携带 `event_id/event_version/state_version`，旧版本 consumer 必须忽略。

## 5. 故障语义

- Kafka 宕机：outbox 积压（PENDING/DEAD 可见），事件不丢，恢复后自动补投。
- 消费者崩溃：offset 未提交 → 重投 → 账本去重；重试耗尽进 DLQ 不阻塞分区。
- DB 故障：启用 outbox 的发布端明确失败且不直发；消费端处理失败走重试/DLQ。调用方不得吞掉
  需要和业务状态原子提交的发布异常。
- 媒体通话：本契约不承载媒体，Kafka/outbox 故障不直接影响已建立的 WebRTC/Media（宪法 §14）。

## 6. 未执行项（诚实声明）

- 未做真实 Kafka 故障注入（Kafka unavailable/lag/consumer crash/duplicate/out-order 的端到端矩阵）；
  当前交付为代码 + 单元测试，真实 Broker 联调与 DLQ 回放待执行。
- 当前通用消费者账本不对 WebSocket、FFmpeg 或对象存储副作用提供 exactly-once；需要业务幂等键和
  真实崩溃窗口测试，不能把“查账后处理、处理后记账”描述为绝对去重。
- `expireAt`/`DelayedQueue` 秒级时间轮仍属 RTC-CALL（CallTtlWorker），不在本契约范围。
