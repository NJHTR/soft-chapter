package com.douyin.kafka.reliability;

import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.KafkaTopicConfig;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.CoverExtractEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.kafka.dto.VideoEvent;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 调度器：把 PENDING 事件幂等投递到 Kafka。
 *
 * <p>设计要点（总任务 §15/§16/§17/§18/§22 对齐）：
 * <ul>
 *   <li>CAS 认领（PENDING→PROCESSING）保证多实例只有一个投递者；</li>
 *   <li>投递后标记 SENT；失败按 2^retry 指数退避重排下次时间，超过
 *       {@code outbox-max-attempts} 标记 DEAD（指标+日志告警，不无限重试）；</li>
 *   <li>PROCESSING 卡死（实例崩溃）由 recoverStale 恢复重新投递；</li>
 *   <li>同步 send + 有界超时（5s），投递成功才记账——不丢事件；</li>
 *   <li>消费者账本负责最终去重，调度器"至少一次"交付即可。</li>
 * </ul>
 * 调度器只承载控制事件，绝不承载媒体字节（宪法 §2.1）。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class EventOutboxDispatcher {

    private final EventOutboxMapper outboxMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaReliabilityProperties properties;
    private final KafkaReliabilityMetrics metrics;

    public EventOutboxDispatcher(EventOutboxMapper outboxMapper,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 KafkaReliabilityProperties properties,
                                 KafkaReliabilityMetrics metrics) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${douyin.kafka.reliability.outbox-poll-ms:1000}")
    public void dispatch() {
        if (!properties.isOutboxEnabled()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<EventOutbox> pending = outboxMapper.findPendingBatch(now, properties.getOutboxBatchSize());
            for (EventOutbox row : pending) {
                dispatchRow(row);
            }
            outboxMapper.recoverStale(
                    now.minusNanos(properties.getOutboxStaleProcessingMs() * 1_000_000L),
                    now.plusSeconds(60));
            metrics.gaugePending(outboxMapper.countPending());
        } catch (Exception e) {
            log.warn("[KAFKA-OUTBOX] dispatch cycle failed", e);
        }
    }

    /** outbox 终态行定期清理（防止无界增长，宪法 §23）。 */
    @Scheduled(fixedDelayString = "${douyin.kafka.reliability.purge-interval-ms:3600000}",
            initialDelayString = "60000")
    public void purgeTerminal() {
        try {
            int removed = outboxMapper.purgeTerminal(
                    LocalDateTime.now().minusHours(properties.getOutboxRetentionHours()));
            if (removed > 0) {
                log.info("[KAFKA-OUTBOX] terminal purge removed={}", removed);
            }
        } catch (Exception e) {
            log.warn("[KAFKA-OUTBOX] terminal purge failed", e);
        }
    }

    private void dispatchRow(EventOutbox row) {
        if (outboxMapper.tryClaim(row.getId()) == 0) {
            return; // 其他实例已认领
        }
        try {
            Object payload = decodePayload(row.getTopic(), row.getPayload());
            kafkaTemplate.send(row.getTopic(), row.getEventKey(), payload)
                    .get(5, TimeUnit.SECONDS);
            outboxMapper.markSent(row.getId());
            metrics.outboxDispatched(row.getTopic());
        } catch (Exception e) {
            handleFailure(row, e);
        }
    }

    /** Restore the DTO type so JsonSerializer emits the type header expected by consumers. */
    private Object decodePayload(String topic, String payload) throws Exception {
        return switch (topic) {
            case KafkaTopicConfig.TOPIC_CHAT_MESSAGE -> objectMapper.readValue(payload, ChatMessageEvent.class);
            case KafkaTopicConfig.TOPIC_GROUP_MESSAGE -> objectMapper.readValue(payload, GroupMessageEvent.class);
            case KafkaTopicConfig.TOPIC_NOTIFICATION -> objectMapper.readValue(payload, NotificationEvent.class);
            case KafkaTopicConfig.TOPIC_VIDEO_EVENTS -> objectMapper.readValue(payload, VideoEvent.class);
            case KafkaTopicConfig.TOPIC_COVER_EXTRACT -> objectMapper.readValue(payload, CoverExtractEvent.class);
            default -> objectMapper.readTree(payload);
        };
    }

    private void handleFailure(EventOutbox row, Exception cause) {
        int attempts = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        if (attempts >= properties.getOutboxMaxAttempts()) {
            outboxMapper.markDead(row.getId());
            metrics.outboxDead(row.getTopic());
            log.error("[KAFKA-OUTBOX] outbox row DEAD: id={} topic={} eventId={} attempts={} error={}",
                    row.getId(), row.getTopic(), row.getEventId(), attempts, cause.getMessage());
            return;
        }
        long backoffMs = Math.min(
                properties.getOutboxBackoffBaseMs() * (1L << Math.min(attempts, 10)),
                properties.getOutboxBackoffMaxMs());
        outboxMapper.scheduleRetry(row.getId(), LocalDateTime.now().plusNanos(backoffMs * 1_000_000L));
        metrics.outboxRetry(row.getTopic());
        log.warn("[KAFKA-OUTBOX] send failed, scheduled retry: id={} topic={} eventId={} attempt={} backoffMs={} error={}",
                row.getId(), row.getTopic(), row.getEventId(), attempts, backoffMs, cause.getMessage());
    }
}
