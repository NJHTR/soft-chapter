package com.douyin.service;

import com.douyin.entity.LiveProviderSession;
import com.douyin.mapper.LiveProviderSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveProviderSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void closeLocksRoomBeforeRetiringTheExactProviderGeneration() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        LiveProviderSessionService service = new LiveProviderSessionService(mapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test
    void rejectsASecondPublisherOnTheSameSrsServerGeneration() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        LiveProviderSession active = session("srs-1:client-1", "client-1", "srs-1", "ACTIVE");
        when(mapper.findByGeneration(99L, "PUBLISH", "stream-1", "srs-1:client-2"))
                .thenReturn(Optional.empty());
        when(mapper.findActivePublish(99L, "stream-1")).thenReturn(Optional.of(active));

        LiveProviderSession accepted = service(mapper).accept(
                99L, "PUBLISH", "client-2", "srs-1", "srs-1:client-2",
                "stream-1", 7L, "on_publish");

        assertNull(accepted);
        verify(mapper).lockLiveRoom(99L);
        verify(mapper, never()).markEnded(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).upsertActive(any());
    }

    @Test
    void treatsANewSrsServerGenerationAsAProviderRestart() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        LiveProviderSession active = session("srs-old:client-1", "client-1", "srs-old", "ACTIVE");
        when(mapper.findByGeneration(99L, "PUBLISH", "stream-1", "srs-new:client-2"))
                .thenReturn(Optional.empty());
        when(mapper.findActivePublish(99L, "stream-1")).thenReturn(Optional.of(active));

        LiveProviderSession accepted = service(mapper).accept(
                99L, "PUBLISH", "client-2", "srs-new", "srs-new:client-2",
                "stream-1", 7L, "on_publish");

        org.junit.jupiter.api.Assertions.assertNotNull(accepted);
        assertEquals("srs-new:client-2", accepted.getProviderSessionId());
        assertEquals("ACTIVE", accepted.getState());
        verify(mapper).markEnded(99L, "PUBLISH", "client-1", "srs-old", "srs-old:client-1",
                "stream-1", "provider_restart", NOW_LOCAL);
        verify(mapper).upsertActive(any(LiveProviderSession.class));
    }

    @Test
    void rejectsAReplayFromAnEndedProviderGeneration() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        LiveProviderSession ended = session("srs-1:client-1", "client-1", "srs-1", "ENDED");
        when(mapper.findByGeneration(99L, "PUBLISH", "stream-1", "srs-1:client-1"))
                .thenReturn(Optional.of(ended));

        LiveProviderSession accepted = service(mapper).accept(
                99L, "PUBLISH", "client-1", "srs-1", "srs-1:client-1",
                "stream-1", 7L, "on_publish");

        assertNull(accepted);
        verify(mapper).lockLiveRoom(99L);
        verify(mapper, never()).findActivePublish(99L, "stream-1");
        verify(mapper, never()).upsertActive(any());
    }

    @Test
    void retiresOnlyTheGenerationSeenByTheReconciliationSnapshot() {
        LiveProviderSessionMapper mapper = mock(LiveProviderSessionMapper.class);
        when(mapper.retirePublishGeneration(eq(99L), eq("stream-1"), eq("srs-old:client-1"),
                eq("generation_changed"), any())).thenReturn(1);

        int retired = service(mapper).retirePublishGeneration(
                99L, "stream-1", "srs-old:client-1", "generation_changed");

        assertEquals(1, retired);
        InOrder calls = inOrder(mapper);
        calls.verify(mapper).lockLiveRoom(99L);
        calls.verify(mapper).retirePublishGeneration(eq(99L), eq("stream-1"),
                eq("srs-old:client-1"), eq("generation_changed"), any());
    }

    private static LiveProviderSessionService service(LiveProviderSessionMapper mapper) {
        return new LiveProviderSessionService(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static LiveProviderSession session(String generation, String clientId,
                                               String serverId, String state) {
        LiveProviderSession session = new LiveProviderSession();
        session.setRoomId(99L);
        session.setProvider("srs");
        session.setDirection("PUBLISH");
        session.setClientId(clientId);
        session.setServerId(serverId);
        session.setProviderSessionId(generation);
        session.setStreamKey("stream-1");
        session.setState(state);
        return session;
    }
}
