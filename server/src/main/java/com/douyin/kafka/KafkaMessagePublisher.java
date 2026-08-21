package com.douyin.kafka;

import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.kafka.dto.VideoEvent;
import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.douyin.kafka.reliability.KafkaReliabilityMetrics;
import com.douyin.kafka.reliability.KafkaReliabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka 消息发布者 — 当 douyin.kafka.enabled=true 时生效。
 *
 * <p>可靠性（总任务 §18）：启用 outbox 时只写 PENDING 行，由
 * {@code EventOutboxDispatcher} 至少一次投递。outbox 写入失败必须向调用方
 * 暴露，禁止绕过数据库直发 Kafka，否则业务提交和事件发布会产生双重事实。
 * 需要与业务状态原子提交的调用方必须在同一个 Spring 事务内调用本发布器。
 * eventId 为空的事件在入 outbox 前自动生成 UUID，供消费端账本去重。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final KafkaReliabilityProperties properties;
    private final KafkaReliabilityMetrics metrics;

    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                 EventOutboxMapper outboxMapper,
                                 ObjectMapper objectMapper,
                                 KafkaReliabilityProperties properties,
                                 KafkaReliabilityMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void publishChat(ChatMessageEvent event) {
        log.info("[KAFKA-PUB] send chat: from={} to={}", event.getFromUserId(), event.getToUserId());
        String topic = KafkaTopicConfig.TOPIC_CHAT_MESSAGE;
        publish(topic, String.valueOf(event.getFromUserId()), event);
    }

    @Override
    public void publishGroupChat(GroupMessageEvent event) {
        log.info("[KAFKA-PUB] send group chat: group={} from={}", event.getGroupId(), event.getFromUserId());
        String topic = KafkaTopicConfig.TOPIC_GROUP_MESSAGE;
        publish(topic, String.valueOf(event.getGroupId()), event);
    }

    @Override
    public void publishNotification(NotificationEvent event) {
        log.info("[KAFKA-PUB] send notify: toUser={} type={}", event.getUserId(), event.getType());
        String topic = KafkaTopicConfig.TOPIC_NOTIFICATION;
        publish(topic, String.valueOf(event.getUserId()), event);
    }

    @Override
    public void publishVideoEvent(VideoEvent event) {
        log.debug("[KAFKA-PUB] video event: action={} userId={} videoId={}", event.getAction(), event.getUserId(), event.getVideoId());
        String topic = KafkaTopicConfig.TOPIC_VIDEO_EVENTS;
        publish(topic, String.valueOf(event.getUserId()), event);
    }

    private void publish(String topic, String key, Object event) {
        ensureEventId(event);
        if (!properties.isOutboxEnabled()) {
            directSend(topic, key, event);
            return;
        }
        enqueue(topic, key, event);
    }

    /** 写 outbox；失败必须传播，不能用直发掩盖一致性故障。 */
    private void enqueue(String topic, String key, Object event) {
        try {
            EventOutbox row = new EventOutbox();
            row.setTopic(topic);
            row.setEventKey(key == null ? "" : key);
            row.setEventId(extractEventId(event));
            row.setPayload(objectMapper.writeValueAsString(event));
            row.setStatus(EventOutbox.STATUS_PENDING);
            row.setRetryCount(0);
            row.setNextAttemptAt(LocalDateTime.now());
            outboxMapper.insert(row);
        } catch (Exception e) {
            metrics.outboxEnqueueFailure(topic);
            log.error("[KAFKA-PUB] outbox enqueue FAILED: topic={} error={}",
                    topic, e.getMessage(), e);
            throw new KafkaPublishException("outbox enqueue failed for topic " + topic, e);
        }
    }

    private void directSend(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA-PUB] direct send FAILED: topic={} error={}", topic, ex.getMessage(), ex);
                    } else {
                        log.info("[KAFKA-PUB] direct send OK: topic={} offset={}",
                                topic, result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }

    private void ensureEventId(Object event) {
        if (event instanceof ChatMessageEvent e && (e.getEventId() == null || e.getEventId().isBlank())) {
            e.setEventId(UUID.randomUUID().toString());
        } else if (event instanceof GroupMessageEvent e && (e.getEventId() == null || e.getEventId().isBlank())) {
            e.setEventId(UUID.randomUUID().toString());
        } else if (event instanceof NotificationEvent e && (e.getEventId() == null || e.getEventId().isBlank())) {
            e.setEventId(UUID.randomUUID().toString());
        } else if (event instanceof VideoEvent e && (e.getEventId() == null || e.getEventId().isBlank())) {
            e.setEventId(UUID.randomUUID().toString());
        }
    }

    private String extractEventId(Object event) {
        if (event instanceof ChatMessageEvent e) return e.getEventId();
        if (event instanceof GroupMessageEvent e) return e.getEventId();
        if (event instanceof NotificationEvent e) return e.getEventId();
        if (event instanceof VideoEvent e) return e.getEventId();
        return null;
    }
}
