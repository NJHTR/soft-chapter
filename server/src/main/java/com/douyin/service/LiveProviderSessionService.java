package com.douyin.service;

import com.douyin.entity.LiveProviderSession;
import com.douyin.mapper.LiveProviderSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Durable, idempotent SRS callback projection. */
@Service
public class LiveProviderSessionService {

    private static final String PROVIDER = "srs";
    private final LiveProviderSessionMapper mapper;
    private final Clock clock;

    public LiveProviderSessionService(LiveProviderSessionMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    LiveProviderSessionService(LiveProviderSessionMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public LiveProviderSession accept(Long roomId, String direction, String clientId, String serverId,
                                      String providerSessionId, String streamKey, Long userId, String event) {
        Optional<LiveProviderSession> sameGeneration = mapper.findByGeneration(
                roomId, direction, streamKey, providerSessionId);
        if (sameGeneration != null && sameGeneration.isPresent()
                && "ENDED".equals(sameGeneration.get().getState())) {
            // A replayed callback from a closed generation must not revive
            // either the projection or the room.
            return null;
        }
        if ("PUBLISH".equals(direction)) {
            Optional<LiveProviderSession> activeResult = mapper.findActivePublish(roomId, streamKey);
            LiveProviderSession active = activeResult == null ? null : activeResult.orElse(null);
            if (active != null && !Objects.equals(active.getProviderSessionId(), providerSessionId)) {
                // A delayed callback from an older publisher generation must
                // not replace the currently active generation.
                return null;
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LiveProviderSession session = new LiveProviderSession();
        session.setRoomId(roomId);
        session.setProvider(PROVIDER);
        session.setDirection(direction);
        session.setClientId(clientId);
        session.setServerId(serverId);
        session.setProviderSessionId(providerSessionId);
        session.setStreamKey(streamKey);
        session.setUserId(userId);
        session.setState("ACTIVE");
        session.setLastEvent(event);
        session.setFirstSeenAt(now);
        session.setLastSeenAt(now);
        session.setEndedAt(null);
        mapper.upsertActive(session);
        return session;
    }

    public int close(Long roomId, String direction, String clientId, String serverId,
                     String providerSessionId, String streamKey, String event) {
        return mapper.markEnded(roomId, direction, clientId, serverId, providerSessionId,
                streamKey, event, LocalDateTime.now(clock));
    }

    public int heartbeat(Long roomId, String providerSessionId, String streamKey) {
        return mapper.touchActivePublish(roomId, providerSessionId, streamKey, LocalDateTime.now(clock));
    }

    public List<LiveProviderSession> activePublishSessions() {
        return mapper.findActivePublishSessions();
    }
}
