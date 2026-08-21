package com.douyin.kafka.reliability;

import com.douyin.kafka.mapper.KafkaEventLedgerMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaEventLedgerServiceTest {

    private final KafkaEventLedgerMapper mapper = mock(KafkaEventLedgerMapper.class);
    private final KafkaReliabilityProperties properties = new KafkaReliabilityProperties();
    private final KafkaReliabilityMetrics metrics =
            new KafkaReliabilityMetrics(new SimpleMeterRegistry());

    private KafkaEventLedgerService newService() {
        return new KafkaEventLedgerService(mapper, metrics, properties);
    }

    @Test
    void nullEventId_isAlwaysProcessedWithoutLedgerCheck() {
        KafkaEventLedgerService svc = newService();
        assertFalse(svc.isProcessed("chat-messages", null));
        assertFalse(svc.isProcessed("chat-messages", "  "));
        verify(mapper, never()).exists(any(), any());
    }

    @Test
    void isProcessed_probesMapper() {
        when(mapper.exists("chat-messages", "e1")).thenReturn(1);
        assertTrue(newService().isProcessed("chat-messages", "e1"));

        when(mapper.exists("chat-messages", "e2")).thenReturn(0);
        assertFalse(newService().isProcessed("chat-messages", "e2"));
    }

    @Test
    void isProcessed_failsOpen_onDbError() {
        when(mapper.exists("chat-messages", "e1")).thenThrow(new RuntimeException("db down"));
        assertFalse(newService().isProcessed("chat-messages", "e1"),
                "账本 DB 异常应 fail-open 放行");
    }

    @Test
    void markProcessed_countsDuplicate_whenInsertIgnored() {
        when(mapper.markProcessed("chat-messages", "e1")).thenReturn(0);
        assertDoesNotThrow(() -> newService().markProcessed("chat-messages", "e1"));
        verify(mapper, times(1)).markProcessed("chat-messages", "e1");
    }

    @Test
    void markProcessed_failsOpen_onDbError() {
        when(mapper.markProcessed("chat-messages", "e1")).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> newService().markProcessed("chat-messages", "e1"),
                "记账失败不应阻断业务处理");
    }

    @Test
    void markProcessedOrThrow_propagatesDbError() {
        when(mapper.markProcessed("chat-messages", "e1")).thenThrow(new RuntimeException("db down"));
        assertThrows(RuntimeException.class,
                () -> newService().markProcessedOrThrow("chat-messages", "e1"));
    }

    @Test
    void purge_usesConfiguredRetention() {
        newService().purgeOldEntries();
        verify(mapper, times(1)).purgeOlderThan(any());
    }
}
