package com.douyin.kafka;

import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 消息发布者 — 当 douyin.kafka.enabled=true 时生效。
 * 聊天消息 → chat-messages topic，通知 → notification-events topic。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishChat(ChatMessageEvent event) {
        log.info("[KAFKA-PUB] send chat: from={} to={}", event.getFromUserId(), event.getToUserId());
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_CHAT_MESSAGE,
                String.valueOf(event.getFromUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA-PUB] chat send FAILED: from={} to={} error={}",
                                event.getFromUserId(), event.getToUserId(), ex.getMessage(), ex);
                    } else {
                        log.info("[KAFKA-PUB] chat send OK: from={} to={} offset={}",
                                event.getFromUserId(), event.getToUserId(),
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }

    @Override
    public void publishGroupChat(GroupMessageEvent event) {
        log.info("[KAFKA-PUB] send group chat: group={} from={}", event.getGroupId(), event.getFromUserId());
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_GROUP_MESSAGE,
                String.valueOf(event.getGroupId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA-PUB] group chat send FAILED: group={} from={} error={}",
                                event.getGroupId(), event.getFromUserId(), ex.getMessage(), ex);
                    } else {
                        log.info("[KAFKA-PUB] group chat send OK: group={} from={} offset={}",
                                event.getGroupId(), event.getFromUserId(),
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }

    @Override
    public void publishNotification(NotificationEvent event) {
        log.info("[KAFKA-PUB] send notify: toUser={} type={}", event.getUserId(), event.getType());
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_NOTIFICATION,
                String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA-PUB] notify send FAILED: toUser={} type={} error={}",
                                event.getUserId(), event.getType(), ex.getMessage(), ex);
                    } else {
                        log.info("[KAFKA-PUB] notify send OK: toUser={} type={} offset={}",
                                event.getUserId(), event.getType(),
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }
}
