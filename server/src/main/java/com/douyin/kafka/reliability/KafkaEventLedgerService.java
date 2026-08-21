package com.douyin.kafka.reliability;

import com.douyin.kafka.mapper.KafkaEventLedgerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消费幂等账本（at-least-once 去重）。
 *
 * <p>时序约定（与 KafkaMessageConsumer/VideoEventConsumer 配合）：
 * <ol>
 *   <li>处理前 {@link #isProcessed} 查账，已处理直接跳过并 ack；</li>
 *   <li>数据库副作用处理成功后在同一事务内用 {@link #markProcessedOrThrow} 记账；</li>
 *   <li>ack。</li>
 * </ol>
 *
 * <p>处理失败不记账 → 重试/DLQ 语义不被账本阻断；记账后、ack 前崩溃 →
 * 重投递时查账命中跳过，避免重复副作用。eventId 为 null 的旧格式事件
 * 不做去重（直接放行），兼容历史 producer。
 *
 * <p>外部副作用可使用 fail-open 的 {@link #markProcessed}；能共享数据库事务的消费者
 * 必须使用 strict variant，让记账失败回滚业务写并禁止 ack。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class KafkaEventLedgerService {

    private final KafkaEventLedgerMapper mapper;
    private final KafkaReliabilityMetrics metrics;
    private final KafkaReliabilityProperties properties;

    public KafkaEventLedgerService(KafkaEventLedgerMapper mapper,
                                   KafkaReliabilityMetrics metrics,
                                   KafkaReliabilityProperties properties) {
        this.mapper = mapper;
        this.metrics = metrics;
        this.properties = properties;
    }

    /** 事件是否已处理过。eventId 为 null 一律放行（旧 producer 兼容）。 */
    public boolean isProcessed(String topic, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        try {
            return mapper.exists(topic, eventId) > 0;
        } catch (Exception e) {
            log.warn("Ledger exists check failed, fail-open: topic={} eventId={}", topic, eventId, e);
            metrics.ledgerFailOpen(topic);
            return false;
        }
    }

    /** 处理成功后记账。重复记账（并发/重投递抢先）按已处理处理。 */
    public void markProcessed(String topic, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            int rows = mapper.markProcessed(topic, eventId);
            if (rows == 0) {
                metrics.ledgerDuplicate(topic);
            }
        } catch (Exception e) {
            log.warn("Ledger mark failed, fail-open: topic={} eventId={}", topic, eventId, e);
            metrics.ledgerFailOpen(topic);
        }
    }

    /** Strict variant for database side effects that share the caller transaction. */
    public void markProcessedOrThrow(String topic, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        int rows = mapper.markProcessed(topic, eventId);
        if (rows == 0) {
            metrics.ledgerDuplicate(topic);
        }
    }

    /** 定时清理超期账本，防止无界增长（宪法 §23 慢查询防护）。 */
    @Scheduled(fixedDelayString = "${douyin.kafka.reliability.purge-interval-ms:3600000}")
    public void purgeOldEntries() {
        try {
            int removed = mapper.purgeOlderThan(LocalDateTime.now().minusHours(properties.getLedgerRetentionHours()));
            if (removed > 0) {
                log.info("[KAFKA-RELIABILITY] ledger purge removed={}", removed);
            }
        } catch (Exception e) {
            log.warn("Ledger purge failed", e);
        }
    }
}
