package com.douyin.service;

import com.douyin.entity.LiveRoom;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveProviderReconciliationTransitionServiceTest {

    @Test
    void retiresTheSnapshotGenerationBeforeApplyingTheExpectedGenerationCas() {
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveService liveService = mock(LiveService.class);
        LiveProviderReconciliationTransitionService transitions =
                new LiveProviderReconciliationTransitionService(sessions, liveService);
        LiveRoom degraded = new LiveRoom();
        when(liveService.providerDisconnected(99L, "stream-1", "srs-1:old-client"))
                .thenReturn(degraded);

        LiveRoom result = transitions.retireAndDisconnect(
                99L, "stream-1", "srs-1:old-client", "provider_missing");

        assertSame(degraded, result);
        var calls = inOrder(sessions, liveService);
        calls.verify(sessions).retirePublishGeneration(
                99L, "stream-1", "srs-1:old-client", "provider_missing");
        calls.verify(liveService).providerDisconnected(99L, "stream-1", "srs-1:old-client");
        verify(liveService, org.mockito.Mockito.never())
                .providerDisconnected(99L, "stream-1", "srs-1:new-client");
    }

    @Test
    void retiresBeforeTheTerminalCasWhenGraceHasExpired() {
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveService liveService = mock(LiveService.class);
        LiveProviderReconciliationTransitionService transitions =
                new LiveProviderReconciliationTransitionService(sessions, liveService);
        LiveRoom ended = new LiveRoom();
        when(liveService.endProviderRoom(99L, "stream-1", "srs-1:old-client", "provider_missing"))
                .thenReturn(ended);

        LiveRoom result = transitions.retireAndEnd(
                99L, "stream-1", "srs-1:old-client", "provider_missing", "provider_missing");

        assertSame(ended, result);
        var calls = inOrder(sessions, liveService);
        calls.verify(sessions).retirePublishGeneration(
                99L, "stream-1", "srs-1:old-client", "provider_missing");
        calls.verify(liveService).endProviderRoom(
                99L, "stream-1", "srs-1:old-client", "provider_missing");
    }

    @Test
    void legacyTransitionDelegatesToTheLockingRetirementPathBeforeDisconnecting() {
        LiveProviderSessionService sessions = mock(LiveProviderSessionService.class);
        LiveService liveService = mock(LiveService.class);
        LiveProviderReconciliationTransitionService transitions =
                new LiveProviderReconciliationTransitionService(sessions, liveService);

        transitions.retireAndDisconnect(99L, "stream-1", null, "provider_missing");

        var calls = inOrder(sessions, liveService);
        calls.verify(sessions).retirePublishGeneration(99L, "stream-1", null, "provider_missing");
        calls.verify(liveService).providerDisconnected(99L, "stream-1", null);
    }

    @Test
    void transitionMethodsAreTransactional() throws NoSuchMethodException {
        assertTrue(LiveProviderReconciliationTransitionService.class
                        .getMethod("retireAndDisconnect", Long.class, String.class, String.class, String.class)
                        .isAnnotationPresent(Transactional.class));
        assertTrue(LiveProviderReconciliationTransitionService.class
                        .getMethod("retireAndEnd", Long.class, String.class, String.class,
                                String.class, String.class)
                        .isAnnotationPresent(Transactional.class));
    }
}
