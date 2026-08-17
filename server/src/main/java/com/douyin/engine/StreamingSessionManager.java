package com.douyin.engine;

import com.douyin.entity.LiveRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active streaming sessions.
 * Each streaming session corresponds to a live room with an active broadcast.
 *
 * Tracks which rooms are using native engine vs legacy WebSocket fallback.
 */
@Slf4j
@Component
public class StreamingSessionManager {

    private final Map<Long, StreamingSession> sessions = new ConcurrentHashMap<>();

    public StreamingSessionManager() {
    }

    /**
     * Start a streaming session for a room.
     */
    public StreamingSession startSession(Long roomId, Long hostUserId,
                                          StreamingEngine.EngineConfig config) {
        StreamingSession session = new StreamingSession(roomId, hostUserId, config);
        sessions.put(roomId, session);
        log.info("Streaming session started: roomId={}, hostUserId={}", roomId, hostUserId);
        return session;
    }

    /**
     * Stop a streaming session.
     */
    public void stopSession(Long roomId) {
        StreamingSession session = sessions.remove(roomId);
        if (session != null) {
            session.shutdown();
            log.info("Streaming session stopped: roomId={}", roomId);
        }
    }

    /**
     * Get active session for a room.
     */
    public StreamingSession getSession(Long roomId) {
        return sessions.get(roomId);
    }

    /**
     * Check if a room has an active streaming session.
     */
    public boolean isActive(Long roomId) {
        return sessions.containsKey(roomId);
    }

    /**
     * Get all active room IDs.
     */
    public Set<Long> getActiveRoomIds() {
        return sessions.keySet();
    }

    /**
     * Get total active stream count.
     */
    public int getActiveCount() {
        return sessions.size();
    }

    /**
     * Represents a single streaming session.
     */
    public static class StreamingSession {
        private final Long roomId;
        private final Long hostUserId;
        private final StreamingEngine engine;
        private final StreamingEngine.EngineConfig config;
        private long startedAt;
        private boolean active = false;

        public StreamingSession(Long roomId, Long hostUserId,
                                StreamingEngine.EngineConfig config) {
            this.roomId = roomId;
            this.hostUserId = hostUserId;
            this.config = config;
            this.engine = new StreamingEngine();
            this.startedAt = System.currentTimeMillis();
        }

        public boolean start() {
            if (engine.init(config)) {
                active = engine.start();
                if (active) {
                    log.info("Session {} streaming active", roomId);
                }
                return active;
            }
            return false;
        }

        public void shutdown() {
            if (active) {
                engine.stop();
                engine.destroy();
                active = false;
                log.info("Session {} streaming stopped", roomId);
            }
        }

        public boolean isActive() { return active; }
        public Long getRoomId() { return roomId; }
        public Long getHostUserId() { return hostUserId; }
        public StreamingEngine getEngine() { return engine; }
        public StreamingEngine.EngineStats getStats() { return engine.getStats(); }
        public long getStartedAt() { return startedAt; }
        public long getUptimeMs() { return System.currentTimeMillis() - startedAt; }
    }
}
