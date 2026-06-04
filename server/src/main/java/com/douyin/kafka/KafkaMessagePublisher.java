package com.douyin.kafka;

import com.douyin.kafka.dto.ChatMessageEvent;
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
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_CHAT_MESSAGE,
                String.valueOf(event.getFromUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Kafka send chat failed: {}", ex.getMessage());
                });
    }

    @Override
    public void publishNotification(NotificationEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_NOTIFICATION,
                String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Kafka send notify failed: {}", ex.getMessage());
                });
    }
}
