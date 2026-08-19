package com.douyin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTL based live-room presence shared by REST join/leave and the control WS.
 * The member identity is (room, user, session), so opening both transports
 * for one browser session cannot increment the room twice.
 */
@Slf4j
@Service
public class LivePresenceService {

    private static final long TTL_MILLIS = Duration.ofSeconds(45).toMillis();
    private static final String KEY_PREFIX = "douyin:live:presence:";

    private final RedisTemplate<String, Object> redis;
    private final AtomicBoolean redisWarningLogged = new AtomicBoolean();

    public LivePresenceService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /** Touch a session and return true only when it was newly added. */
    public boolean touch(Long roomId, Long userId, String sessionId) {
        sessionId = normalizeSessionId(sessionId);
        if (sessionId == null || roomId == null || userId == null) {
            return false;
        }
        String member = member(userId, sessionId);
        long now = System.currentTimeMillis();
        try {
            var zset = redis.opsForZSet();
            zset.removeRangeByScore(key(roomId), 0, now);
            Boolean added = zset.add(key(roomId), member, now + TTL_MILLIS);
            return Boolean.TRUE.equals(added);
        } catch (Exception e) {
            warnRedis(e);
            // A node-local count is not a valid viewer count in a multi-node
            // deployment. Keep it as an explicit development-only opt-in.
            return false;
        }
    }

    /** Remove a session and return true only when it was present. */
    public boolean leave(Long roomId, Long userId, String sessionId) {
        sessionId = normalizeSessionId(sessionId);
        if (sessionId == null || roomId == null || userId == null) {
            return false;
        }
        String member = member(userId, sessionId);
        try {
            Long removed = redis.opsForZSet().remove(key(roomId), member);
            return removed != null && removed > 0;
        } catch (Exception e) {
            warnRedis(e);
            return false;
        }
    }

    /** Return active sessions; expired Redis members are pruned on read. */
    public int count(Long roomId) {
        long now = System.currentTimeMillis();
        try {
            var zset = redis.opsForZSet();
            zset.removeRangeByScore(key(roomId), 0, now);
            Long size = zset.zCard(key(roomId));
            return size == null ? 0 : Math.toIntExact(size);
        } catch (Exception e) {
            warnRedis(e);
            return 0;
        }
    }

    private String key(Long roomId) {
        return KEY_PREFIX + roomId;
    }

    private String member(Long userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        return normalized.isEmpty() || normalized.length() > 128 ? null : normalized;
    }

    private void warnRedis(Exception e) {
        if (redisWarningLogged.compareAndSet(false, true)) {
            log.warn("Live presence Redis unavailable; presence operations fail closed: {}", e.getMessage());
        }
    }
}
