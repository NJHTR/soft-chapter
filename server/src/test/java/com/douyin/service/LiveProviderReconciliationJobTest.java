package com.douyin.service;

import com.douyin.entity.LiveRoom;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveProviderReconciliationJobTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void reachableProviderMissingStreamStartsGraceInsteadOfEndingImmediately() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of()));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService).providerDisconnected(99L, "stream-1", "srs-1:client-1");
        verify(liveService, never()).endProviderRoom(eq(99L), eq("stream-1"), eq("srs-1:client-1"), eq("provider_missing"));
    }

    @Test
    void unreachableProviderOnlyDegradesAndDoesNotConsumeGrace() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        room.setProviderGraceUntil(LocalDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).minusSeconds(1));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(LiveProviderClient.LiveProviderSnapshot.unavailable());

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService).providerUnavailable(99L, "stream-1");
        verify(liveService, never()).endProviderRoom(eq(99L), eq("stream-1"), eq("srs-1:client-1"), eq("provider_missing"));
    }

    @Test
    void reachableProviderMissingStreamAfterGraceEndsCurrentGeneration() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of()));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService).endProviderRoom(99L, "stream-1", "srs-1:client-1", "provider_missing");
    }

    @Test
    void legacyRoomWithNoDurableGenerationNeverTreatsAStreamNameAsAuthoritative() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room(null);
        room.setStatus("DEGRADED");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of("stream-1")));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService).providerDisconnected(99L, "stream-1", null);
        verify(liveService, never()).providerHeartbeat(99L, "stream-1", null);
        verify(sessions, never()).heartbeat(99L, null, "stream-1");
    }

    @Test
    void matchingProviderGenerationRefreshesOnlyTheCurrentProjection() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-1:client-1"), "srs-1"));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(sessions).heartbeat(99L, "srs-1:client-1", "stream-1");
        verify(liveService).providerHeartbeat(99L, "stream-1", "srs-1:client-1");
        verify(liveService, never()).providerUnavailable(99L, "stream-1");
        verify(sessions, never()).retirePublishGeneration(
                99L, "stream-1", "srs-1:client-1", "generation_changed");
    }

    @Test
    void changedProviderGenerationRetiresOldProjectionWithoutRevivingTheRoom() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-2:client-2"), "srs-2"));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(sessions).retirePublishGeneration(
                99L, "stream-1", "srs-1:client-1", "generation_changed");
        verify(liveService).providerDisconnected(99L, "stream-1", "srs-1:client-1");
        verify(sessions, never()).heartbeat(99L, "srs-1:client-1", "stream-1");
        verify(liveService, never()).providerHeartbeat(99L, "stream-1", "srs-1:client-1");
    }

    @Test
    void legacyRoomWithFutureGraceDoesNotTrustAStreamNameOrEndEarly() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room(null);
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-1:client-1"), "srs-1"));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService, never()).providerHeartbeat(99L, "stream-1", null);
        verify(liveService, never()).providerDisconnected(99L, "stream-1", null);
        verify(liveService, never()).endProviderRoom(
                99L, "stream-1", null, "unverified_provider_generation");
        verify(sessions, never()).heartbeat(99L, null, "stream-1");
    }

    @Test
    void legacyRoomWithExpiredGraceEndsOnlyAfterTheBoundedWindow() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room(null);
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-1:client-1"), "srs-1"));

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(liveService).endProviderRoom(99L, "stream-1", null, "unverified_provider_generation");
        verify(liveService, never()).providerHeartbeat(99L, "stream-1", null);
        verify(sessions, never()).heartbeat(99L, null, "stream-1");
    }

    @Test
    void staleAbsenceSnapshotTransitionsOnlyItsCapturedGeneration() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveProviderReconciliationTransitionService transitions = mock(
                LiveProviderReconciliationTransitionService.class);
        LiveRoom room = room("srs-1:old-client");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of()));

        new LiveProviderReconciliationJob(liveService, sessions, provider, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        // The room list is a point-in-time snapshot. A newer on_publish can
        // acquire the room lock after this list was read; the transition must
        // carry the old generation into the DB CAS instead of widening to any
        // current publisher.
        verify(transitions).retireAndDisconnect(
                99L, "stream-1", "srs-1:old-client", "provider_missing");
        verify(transitions, never()).retireAndDisconnect(
                99L, "stream-1", "srs-1:new-client", "provider_missing");
        verify(liveService, never()).providerDisconnected(
                99L, "stream-1", "srs-1:old-client");
    }

    @Test
    void generationMismatchUsesTheTransactionalRetireAndDisconnectTransition() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveProviderReconciliationTransitionService transitions = mock(
                LiveProviderReconciliationTransitionService.class);
        LiveRoom room = room("srs-1:old-client");
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-2:new-client"), "srs-2"));

        new LiveProviderReconciliationJob(liveService, sessions, provider, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(transitions).retireAndDisconnect(
                99L, "stream-1", "srs-1:old-client", "generation_changed");
        verify(sessions, never()).retirePublishGeneration(
                99L, "stream-1", "srs-1:old-client", "generation_changed");
    }

    @Test
    void absenceAfterGraceUsesTheTransactionalRetireAndEndTransition() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveProviderReconciliationTransitionService transitions = mock(
                LiveProviderReconciliationTransitionService.class);
        LiveRoom room = room("srs-1:old-client");
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of()));

        new LiveProviderReconciliationJob(liveService, sessions, provider, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(transitions).retireAndEnd(99L, "stream-1", "srs-1:old-client",
                "provider_missing", "provider_missing");
        verify(liveService, never()).endProviderRoom(
                99L, "stream-1", "srs-1:old-client", "provider_missing");
    }

    @Test
    void endingRoomWithUnreachableProviderIsLeftAlone() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveRoom room = room("srs-1:client-1");
        room.setStatus("ENDING");
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(LiveProviderClient.LiveProviderSnapshot.unavailable());

        new LiveProviderReconciliationJob(liveService, sessions, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        // A host-requested shutdown owns its bounded ENDING window; an API
        // outage must not rewrite it to DEGRADED or consume the deadline.
        verify(liveService, never()).providerUnavailable(99L, "stream-1");
        verify(liveService, never()).endProviderRoom(99L, "stream-1", "srs-1:client-1", "provider_missing");
    }

    @Test
    void endingRoomWithReachableAbsentStreamAfterGraceEndsExactlyItsGeneration() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveProviderReconciliationTransitionService transitions = mock(
                LiveProviderReconciliationTransitionService.class);
        LiveRoom room = room("srs-1:client-1");
        room.setStatus("ENDING");
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(true, Set.of()));

        new LiveProviderReconciliationJob(liveService, sessions, provider, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(transitions).retireAndEnd(99L, "stream-1", "srs-1:client-1",
                "provider_missing", "provider_missing");
        verify(liveService, never()).providerUnavailable(99L, "stream-1");
        verify(liveService, never()).providerHeartbeat(99L, "stream-1", "srs-1:client-1");
    }

    @Test
    void endingRoomWithActiveStreamIsLeftAloneUntilItsExactCloseEvent() {
        LiveService liveService = mock(LiveService.class);
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveProviderClient provider = mock(LiveProviderClient.class);
        LiveProviderReconciliationTransitionService transitions = mock(
                LiveProviderReconciliationTransitionService.class);
        LiveRoom room = room("srs-1:client-1");
        room.setStatus("ENDING");
        room.setProviderGraceUntil(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(liveService.listProviderRooms()).thenReturn(List.of(room));
        when(provider.snapshot()).thenReturn(new LiveProviderClient.LiveProviderSnapshot(
                true, Set.of("stream-1"), Map.of("stream-1", "srs-1:client-1"), "srs-1"));

        new LiveProviderReconciliationJob(liveService, sessions, provider, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC)).reconcileOnce();

        verify(transitions, never()).retireAndEnd(
                99L, "stream-1", "srs-1:client-1", "provider_missing", "provider_missing");
        verify(liveService, never()).providerHeartbeat(99L, "stream-1", "srs-1:client-1");
        verify(liveService, never()).providerUnavailable(99L, "stream-1");
    }

    private static LiveRoom room(String providerSessionId) {
        LiveRoom room = new LiveRoom();
        room.setId(99L);
        room.setSrtStreamId("stream-1");
        room.setStatus("DEGRADED");
        room.setProviderSessionId(providerSessionId);
        return room;
    }
}
