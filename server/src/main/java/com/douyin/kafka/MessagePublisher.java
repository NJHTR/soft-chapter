package com.douyin.kafka;

import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.kafka.dto.VideoEvent;

/**
 * 消息发布抽象 — 运行时根据配置选择 Kafka 或直写 DB。
 */
public interface MessagePublisher {
    void publishChat(ChatMessageEvent event);
    void publishNotification(NotificationEvent event);
    void publishGroupChat(GroupMessageEvent event);
    void publishVideoEvent(VideoEvent event);
}
