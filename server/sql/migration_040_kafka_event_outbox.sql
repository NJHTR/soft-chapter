-- Kafka 事务 Outbox（生产者侧事件持久化）
-- 业务侧只写 outbox 行（与业务状态各自事务或同一事务），由 EventOutboxDispatcher 幂等投递到 Kafka。
-- 禁止在 outbox 中存放媒体字节（宪法 §2.1/§38：Kafka 只承载控制事件）。
CREATE TABLE IF NOT EXISTS event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic VARCHAR(64) NOT NULL COMMENT '目标 Kafka topic',
    event_key VARCHAR(128) NOT NULL DEFAULT '' COMMENT '分区 key（如 userId/groupId）',
    event_id VARCHAR(64) NOT NULL COMMENT '业务事件幂等 ID（同一 topic 内唯一）',
    payload MEDIUMTEXT NOT NULL COMMENT '事件 JSON 体（控制事件，非媒体）',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SENT/DEAD',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL COMMENT '下次投递时间（失败指数退避）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_topic_event (topic, event_id),
    KEY idx_outbox_claim (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Kafka transactional outbox';
