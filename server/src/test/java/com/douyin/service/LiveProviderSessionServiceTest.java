package com.douyin.service;

import com.douyin.mapper.LiveProviderSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveProviderSessionServiceTest {

    @Test
    void closeLocksRoomBeforeRetiringTheExactProviderGeneration() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        LiveProviderSessionService service = new LiveProviderSessionService(mapper,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        when(mapper.markEnded(eq(99L), eq("PUBLISH"), eq("old-client"), eq("srs-1"),
                eq("srs-1:old-client"), eq("stream-key"), eq("on_unpublish"), any()))
                .thenReturn(1);

        int closed = service.close(99L, "PUBLISH", "old-client", "srs-1", "srs-1:old-client",
                "stream-key", "on_unpublish");

        assertEquals(1, closed);
        InOrder calls = inOrder(mapper);
        calls.verify(mapper).lockLiveRoom(99L);
        calls.verify(mapper).markEnded(eq(99L), eq("PUBLISH"), eq("old-client"), eq("srs-1"),
                eq("srs-1:old-client"), eq("stream-key"), eq("on_unpublish"), any());
    }

    @Test
    void closeUsesATransactionBoundary() throws NoSuchMethodException {
        assertTrue(LiveProviderSessionService.class
                        .getMethod("close", Long.class, String.class, String.class, String.class,
                                String.class, String.class, String.class)
                        .isAnnotationPresent(Transactional.class),
                "the close lock and session update must commit or roll back together");
    }
}
