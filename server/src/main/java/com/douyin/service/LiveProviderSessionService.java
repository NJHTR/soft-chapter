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
        mapper.lockLiveRoom(roomId);
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
                if (!Objects.equals(active.getServerId(), serverId)) {
                    mapper.markEnded(roomId, "PUBLISH", active.getClientId(), active.getServerId(),
                            active.getProviderSessionId(), streamKey, "provider_restart",
                            LocalDateTime.now(clock));
                } else {
                    // A second client on the same SRS generation is a
                    // concurrent publisher, not a restart.
                    return null;
                }
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

    @Transactional
    public int retirePublishGeneration(Long roomId, String streamKey, String providerSessionId, String event) {
        if (providerSessionId == null || providerSessionId.isBlank()) return 0;
        // Reconciliation may be operating on a snapshot taken before a new
        // on_publish callback arrived. Lock and target only the generation
        // observed by that snapshot; never retire whichever session happens
        // to be active at the time the update reaches the database.
        mapper.lockLiveRoom(roomId);
        return mapper.retirePublishGeneration(roomId, streamKey, providerSessionId, event,
                LocalDateTime.now(clock));
    }

    @Transactional
    public int close(Long roomId, String direction, String clientId, String serverId,
                     String providerSessionId, String streamKey, String event) {
        // The callback may be a delayed event from an older provider
        // generation. Serialize it with publish admission on the room row so
        // an old unpublish cannot retire a newly admitted publisher.
        mapper.lockLiveRoom(roomId);
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
