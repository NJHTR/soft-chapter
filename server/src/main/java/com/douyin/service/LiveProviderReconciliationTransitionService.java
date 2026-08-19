package com.douyin.service;

import com.douyin.entity.LiveRoom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically retires a provider generation and moves its room into the
 * bounded disconnect grace state. The provider snapshot may have been taken
 * before a newer callback acquired the room lock, so every room transition is
 * still guarded by the expected provider session id in {@link LiveService}.
 */
@Service
public class LiveProviderReconciliationTransitionService {

    private final LiveProviderSessionService sessionService;
    private final LiveService liveService;

    public LiveProviderReconciliationTransitionService(
            LiveProviderSessionService sessionService,
            LiveService liveService) {
        this.sessionService = sessionService;
        this.liveService = liveService;
    }

    /**
     * Retire only {@code providerSessionId}, then apply the matching room CAS
     * while the same transaction still owns the room row lock. If a newer
     * on_publish committed first, the session update affects zero rows and
     * the expected-generation room CAS becomes a no-op.
     */
    @Transactional
    public LiveRoom retireAndDisconnect(Long roomId, String streamKey,
                                        String providerSessionId, String event) {
        sessionService.retirePublishGeneration(roomId, streamKey, providerSessionId, event);
        return liveService.providerDisconnected(roomId, streamKey, providerSessionId);
    }

    /**
     * The expiry path uses the same lock/retirement boundary before ending a
     * room. This prevents a replacement callback from being admitted between
     * session retirement and the terminal room CAS.
     */
    @Transactional
    public LiveRoom retireAndEnd(Long roomId, String streamKey,
                                 String providerSessionId, String event, String reason) {
        sessionService.retirePublishGeneration(roomId, streamKey, providerSessionId, event);
        return liveService.endProviderRoom(roomId, streamKey, providerSessionId, reason);
    }
}
