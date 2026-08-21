-- Kafka 消费幂等账本（消费者侧）
-- at-least-once 语义下用于去重：消费成功后才 INSERT IGNORE 记账；
-- 处理前先查账，已处理的事件直接跳过并 ack。账本保留期由
-- douyin.kafka.reliability.ledger-retention-hours 控制（默认 744h，即至少 31 天），
-- 由 KafkaEventLedgerService 定时清理，防止无界增长（宪法 §23 慢查询防护）。
CREATE TABLE IF NOT EXISTS kafka_event_ledger (
    topic VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (topic, event_id),
    KEY idx_ledger_processed_at (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Kafka consumer idempotency ledger';
