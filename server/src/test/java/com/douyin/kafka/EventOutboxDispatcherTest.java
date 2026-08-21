package com.douyin.kafka;

import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.dto.VideoEvent;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.douyin.kafka.reliability.EventOutboxDispatcher;
import com.douyin.kafka.reliability.KafkaReliabilityMetrics;
import com.douyin.kafka.reliability.KafkaReliabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventOutboxDispatcherTest {

    private final EventOutboxMapper outboxMapper = mock(EventOutboxMapper.class);
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaReliabilityProperties properties = new KafkaReliabilityProperties();
    private final KafkaReliabilityMetrics metrics =
            new KafkaReliabilityMetrics(new SimpleMeterRegistry());

    private EventOutboxDispatcher newDispatcher() {
        return new EventOutboxDispatcher(outboxMapper, kafkaTemplate, objectMapper, properties, metrics);
    }

    private EventOutbox row(long id, String topic, int retryCount) {
        EventOutbox row = new EventOutbox();
        row.setId(id);
        row.setTopic(topic);
        row.setEventKey("1");
        row.setEventId("event-" + id);
        row.setPayload("{\"action\":\"LIKE\",\"userId\":1}");
        row.setStatus(EventOutbox.STATUS_PENDING);
        row.setRetryCount(retryCount);
        row.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return row;
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatch_marksSent_onSuccessfulSend() {
        when(outboxMapper.findPendingBatch(any(), anyInt())).thenReturn(List.of(row(1L, "video-events", 0)));
        when(outboxMapper.tryClaim(1L)).thenReturn(1);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(mock(SendResult.class)));

        newDispatcher().dispatch();

        verify(outboxMapper, times(1)).markSent(1L);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("video-events"), eq("1"), payload.capture());
        assertInstanceOf(VideoEvent.class, payload.getValue());
        verify(outboxMapper, never()).scheduleRetry(anyLong(), any());
        verify(outboxMapper, never()).markDead(anyLong());
        verify(outboxMapper, atLeastOnce()).recoverStale(any(), any());
    }

    @Test
    void dispatch_skips_rowClaimedByAnotherInstance() {
        when(outboxMapper.findPendingBatch(any(), anyInt())).thenReturn(List.of(row(1L, "chat-messages", 0)));
        when(outboxMapper.tryClaim(1L)).thenReturn(0);

        newDispatcher().dispatch();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(outboxMapper, never()).markSent(anyLong());
    }

    @Test
    void dispatch_schedulesRetry_onSendFailure_withExponentialBackoff() {
        when(outboxMapper.findPendingBatch(any(), anyInt())).thenReturn(List.of(row(1L, "chat-messages", 0)));
        when(outboxMapper.tryClaim(1L)).thenReturn(1);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker down"));

        EventOutboxDispatcher dispatcher = newDispatcher();
        dispatcher.dispatch();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper, times(1)).scheduleRetry(eq(1L), captor.capture());
        // retry 0 -> base 1000ms 附近
        assertTrue(captor.getValue().isAfter(LocalDateTime.now().plusSeconds(0)),
                "next_attempt_at 应晚于当前时间");
        assertTrue(captor.getValue().isBefore(LocalDateTime.now().plusSeconds(5)),
                "next_attempt_at 应在基础退避窗口内");
        verify(outboxMapper, never()).markDead(anyLong());
    }

    @Test
    void dispatch_marksDead_whenExhausted() {
        when(outboxMapper.findPendingBatch(any(), anyInt()))
                .thenReturn(List.of(row(1L, "video-events", properties.getOutboxMaxAttempts() - 1)));
        when(outboxMapper.tryClaim(1L)).thenReturn(1);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker down"));

        newDispatcher().dispatch();

        verify(outboxMapper, times(1)).markDead(1L);
        verify(outboxMapper, never()).scheduleRetry(anyLong(), any());
    }
}
