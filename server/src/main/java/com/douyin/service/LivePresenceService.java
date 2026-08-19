package com.douyin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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

    /*
     * Keep expiry pruning, membership mutation, and key lifecycle in one Redis
     * operation. A read-then-delete implementation can delete a session that
     * concurrently rejoined on another Spring instance.
     */
    private static final StringRedisSerializer SCRIPT_SERIALIZER = StringRedisSerializer.UTF_8;
    private static final DefaultRedisScript<String> TOUCH_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local expiresAt = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            local added = redis.call('ZADD', KEYS[1], expiresAt, ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return tostring(added)
            """, String.class);
    private static final DefaultRedisScript<String> LEAVE_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local removed = redis.call('ZREM', KEYS[1], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
            else
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
            end
            return tostring(removed)
            """, String.class);
    private static final DefaultRedisScript<String> COUNT_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            local count = redis.call('ZCARD', KEYS[1])
            if count == 0 then
                redis.call('DEL', KEYS[1])
            else
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return tostring(count)
            """, String.class);

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
            String added = redis.execute(TOUCH_SCRIPT, SCRIPT_SERIALIZER, SCRIPT_SERIALIZER, List.of(key(roomId)),
                    String.valueOf(now), String.valueOf(now + TTL_MILLIS), member, String.valueOf(TTL_MILLIS));
            return "1".equals(added);
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
            String removed = redis.execute(LEAVE_SCRIPT, SCRIPT_SERIALIZER, SCRIPT_SERIALIZER, List.of(key(roomId)),
                    String.valueOf(System.currentTimeMillis()), member, String.valueOf(TTL_MILLIS));
            return "1".equals(removed);
        } catch (Exception e) {
            warnRedis(e);
            return false;
        }
    }

    /** Return active sessions; expired Redis members are pruned on read. */
    public int count(Long roomId) {
        long now = System.currentTimeMillis();
        try {
            String size = redis.execute(COUNT_SCRIPT, SCRIPT_SERIALIZER, SCRIPT_SERIALIZER, List.of(key(roomId)),
                    String.valueOf(now), String.valueOf(TTL_MILLIS));
            return size == null ? 0 : Integer.parseInt(size);
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
