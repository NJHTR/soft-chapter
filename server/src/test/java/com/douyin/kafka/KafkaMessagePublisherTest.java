package com.douyin.kafka;

import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.douyin.kafka.reliability.KafkaReliabilityMetrics;
import com.douyin.kafka.reliability.KafkaReliabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaMessagePublisherTest {

    private final EventOutboxMapper outboxMapper = mock(EventOutboxMapper.class);
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaReliabilityProperties properties = new KafkaReliabilityProperties();
    private final KafkaReliabilityMetrics metrics =
            new KafkaReliabilityMetrics(new SimpleMeterRegistry());

    private KafkaMessagePublisher newPublisher() {
        return new KafkaMessagePublisher(kafkaTemplate, outboxMapper, objectMapper, properties, metrics);
    }

    @Test
    void publishChat_writesOutbox_withGeneratedEventId() throws Exception {
        properties.setOutboxEnabled(true);
        when(outboxMapper.insert(any(EventOutbox.class))).thenReturn(1);

        ChatMessageEvent event = new ChatMessageEvent();
        event.setFromUserId(1L);
        event.setToUserId(2L);
        event.setContent("hello");
        event.setTimestamp(System.currentTimeMillis());

        newPublisher().publishChat(event);

        verify(outboxMapper, times(1)).insert(org.mockito.ArgumentMatchers.<EventOutbox>argThat(row -> {
            assertNotNull(row.getEventId(), "eventId 必须由发布器自动生成");
            assertEquals(KafkaTopicConfig.TOPIC_CHAT_MESSAGE, row.getTopic());
            assertNotNull(row.getNextAttemptAt());
            assertEquals(EventOutbox.STATUS_PENDING, row.getStatus());
            return true;
        }));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertNotNull(event.getEventId(), "原事件应被回填 eventId");
    }

    @Test
    void publishNotification_failsVisibleWithoutDirectSend_whenOutboxInsertFails() {
        properties.setOutboxEnabled(true);
        when(outboxMapper.insert(any(EventOutbox.class))).thenThrow(new RuntimeException("db down"));
        when(kafkaTemplate.send(eq(KafkaTopicConfig.TOPIC_NOTIFICATION), anyString(), any()))
                .thenReturn(new java.util.concurrent.CompletableFuture<>());

        var publisher = newPublisher();
        com.douyin.kafka.dto.NotificationEvent event = new com.douyin.kafka.dto.NotificationEvent();
        event.setUserId(1L);
        event.setFromUserId(2L);
        event.setType(1);
        event.setContent("关注了你");

        assertThrows(KafkaPublishException.class, () -> publisher.publishNotification(event));

        verify(kafkaTemplate, never()).send(eq(KafkaTopicConfig.TOPIC_NOTIFICATION), anyString(), any());
    }

    @Test
    void publishGroupChat_directSend_whenOutboxDisabled() {
        properties.setOutboxEnabled(false);
        when(kafkaTemplate.send(eq(KafkaTopicConfig.TOPIC_GROUP_MESSAGE), anyString(), any()))
                .thenReturn(new java.util.concurrent.CompletableFuture<>());

        var publisher = newPublisher();
        com.douyin.kafka.dto.GroupMessageEvent event = new com.douyin.kafka.dto.GroupMessageEvent();
        event.setGroupId(5L);
        event.setFromUserId(1L);
        event.setContent("hi");

        publisher.publishGroupChat(event);

        verify(outboxMapper, never()).insert(any(EventOutbox.class));
        verify(kafkaTemplate, times(1)).send(eq(KafkaTopicConfig.TOPIC_GROUP_MESSAGE), anyString(), any());
    }
}
