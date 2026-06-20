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
    public static final String TOPIC_GROUP_MESSAGE = "group-messages";
    public static final String TOPIC_VIDEO_EVENTS = "video-events";
    public static final String TOPIC_COVER_EXTRACT = "cover-extract";

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

    /** 群聊消息 Topic — 3 分区 */
    @Bean
    public NewTopic groupMessageTopic() {
        return TopicBuilder.name(TOPIC_GROUP_MESSAGE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 视频互动事件 Topic — 6 分区（高吞吐，播放/点赞/收藏） */
    @Bean
    public NewTopic videoEventsTopic() {
        return TopicBuilder.name(TOPIC_VIDEO_EVENTS)
                .partitions(6)
                .replicas(1)
                .build();
    }

    /** 封面提取任务 Topic — 1 分区（单线程有序处理，避免并发写同一个视频封面） */
    @Bean
    public NewTopic coverExtractTopic() {
        return TopicBuilder.name(TOPIC_COVER_EXTRACT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
