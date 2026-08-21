# RTC Call Lifecycle Recovery

## Runtime knobs

```text
rtc.call.ringing-ttl=180s
rtc.call.negotiating-ttl=5m
rtc.call.history-retention=31d
rtc.call.history-purge-interval-ms=3600000
rtc.call.timeout-shards=32
rtc.call.timeout-batch-size=500
rtc.call.timeout-poll-ms=1000
douyin.kafka.reliability.outbox-stale-processing-ms=300000
```

## Redis timeout recovery

1. Do not force-end CONNECTED media when Redis is unavailable.
2. Alert on `[CALL-TIMEOUT] Redis index unavailable`; the durable call rows remain authoritative.
3. After the first successful Redis access, the worker runs a bounded `id > lastId` recovery over only
   `RINGING/NEGOTIATING` rows with `expires_at` and rebuilds ZSET members.
4. Verify overdue calls converge through `state + state_version` CAS and inspect timeout lag. Never enable a
   one-second MySQL expiration scan as an emergency fallback.

## Kafka/outbox recovery

1. `event_outbox.PENDING/PROCESSING` is safe to retry; duplicate Kafka records are expected.
2. `DEAD` requires an audited replay using the original `event_id` and `event_key=callId`.
3. Do not edit event versions. Consumers discard `event_version < localVersion` and reconcile equal-version
   duplicates by `event_id`.
4. Kafka failure must not disconnect LiveKit/P2P media. The control client reconciles from MySQL after reconnect.

## Client convergence

- On every authenticated WebSocket connect, expect one `rtc.call.reconciliation` envelope.
- Apply `rtc.call.state` only when `state_version >= localVersion`; duplicate event ids are no-ops.
- If a command returns a terminal state different from optimistic UI, replace local state immediately and stop
  ringing on all devices.
- If notification delivery is uncertain, call `GET /api/rtc/calls/active`; do not synthesize a terminal state.

## History retention

`CallHistoryRetentionJob` deletes only terminal `rtc_call_session` rows older than the configured
retention, after deleting their event and participant children. It is bounded by
`rtc.call.history-purge-batch-size` and never selects active calls. Kafka consumer ledger and
outbox terminal rows use the same 31-day minimum in the portable control-plane profile.
