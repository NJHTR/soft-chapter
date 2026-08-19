package com.douyin.service;

import com.douyin.entity.LiveRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Converges provider state after callback loss or SRS restart. Provider API
 * outages degrade rooms but never end them; only a reachable provider that
 * confirms a stream is absent can consume the bounded grace window.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "live.media.reconciliation.enabled", havingValue = "true")
public class LiveProviderReconciliationJob {

    private final LiveService liveService;
    private final LiveProviderSessionService sessionService;
    private final LiveProviderClient providerClient;
    private final Clock clock;

    public LiveProviderReconciliationJob(LiveService liveService,
                                         LiveProviderSessionService sessionService,
                                         LiveProviderClient providerClient) {
        this(liveService, sessionService, providerClient, Clock.systemUTC());
    }

    LiveProviderReconciliationJob(LiveService liveService,
                                  LiveProviderSessionService sessionService,
                                  LiveProviderClient providerClient,
                                  Clock clock) {
        this.liveService = liveService;
        this.sessionService = sessionService;
        this.providerClient = providerClient;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${live.media.reconciliation.interval-ms:15000}", initialDelayString = "${live.media.reconciliation.initial-delay-ms:10000}")
    public void reconcile() {
        reconcileOnce();
    }

    void reconcileOnce() {
        List<LiveRoom> rooms = liveService.listProviderRooms();
        if (rooms.isEmpty()) return;

        LiveProviderClient.LiveProviderSnapshot snapshot = providerClient.snapshot();
        if (!snapshot.reachable()) {
            for (LiveRoom room : rooms) {
                liveService.providerUnavailable(room.getId(), room.getSrtStreamId());
            }
            return;
        }

        Set<String> activeStreams = snapshot.activePublishStreams();
        LocalDateTime now = LocalDateTime.now(clock);
        for (LiveRoom room : rooms) {
            String streamKey = room.getSrtStreamId();
            if (streamKey == null || streamKey.isBlank()) continue;
            if (activeStreams.contains(streamKey)) {
                if (room.getProviderSessionId() == null || room.getProviderSessionId().isBlank()) {
                    // A row created before provider generations were durable
                    // cannot be trusted merely because a stream with the same
                    // name exists. Give a fresh authenticated on_publish a
                    // bounded chance to claim it, then converge safely.
                    LocalDateTime graceUntil = room.getProviderGraceUntil();
                    if (graceUntil == null) {
                        liveService.providerDisconnected(room.getId(), streamKey, null);
                    } else if (!now.isBefore(graceUntil)) {
                        liveService.endProviderRoom(
                                room.getId(), streamKey, null, "unverified_provider_generation");
                    }
                    continue;
                }
                sessionService.heartbeat(room.getId(), room.getProviderSessionId(), streamKey);
                liveService.providerHeartbeat(room.getId(), streamKey, room.getProviderSessionId());
                continue;
            }

            LocalDateTime graceUntil = room.getProviderGraceUntil();
            if (graceUntil == null) {
                liveService.providerDisconnected(room.getId(), streamKey, room.getProviderSessionId());
            } else if (!now.isBefore(graceUntil)) {
                LiveRoom ended = liveService.endProviderRoom(
                        room.getId(), streamKey, room.getProviderSessionId(), "provider_missing");
                if (ended != null) {
                    log.info("Ended stale live provider room id={} reason=provider_missing", room.getId());
                }
            }
        }
    }
}
