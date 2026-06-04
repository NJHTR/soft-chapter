package com.douyin.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    public static final String TOPIC_CHAT_MESSAGE = "chat-messages";
    public static final String TOPIC_NOTIFICATION = "notification-events";

    /** 聊天消息 Topic — 3 分区，消费端并行处理 */
    @Bean
    public NewTopic chatMessageTopic() {
        return TopicBuilder.name(TOPIC_CHAT_MESSAGE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 互动通知 Topic — 3 分区，点赞/评论/关注等按类型 key 路由到同一分区 */
    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATION)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
