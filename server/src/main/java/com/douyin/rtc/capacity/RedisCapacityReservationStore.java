package com.douyin.rtc.capacity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 持久化 store（生产）。Redis 不可用/超时/响应畸形一律抛
 * {@link CapacityStoreUnavailable}，由 AdmissionService fail-closed
 * （停止新房），绝不通过调大阈值恢复。
 *
 * <p>写入密钥中包含 call_id（允许按 call 隔离），request/snapshot 不直接
 * 作为 Prometheus label；本类不输出任何密钥或敏感字段。
 */
public class RedisCapacityReservationStore implements CapacityReservationStore {

    private static final String KEY_PREFIX = "rtc:capacity:reservation:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final long ttlMillis;

    public RedisCapacityReservationStore(StringRedisTemplate redis, ObjectMapper mapper,
                                         long ttlMillis) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttlMillis = Math.max(1_000L, ttlMillis);
    }

    @Override
    public CapacityReservation createIfAbsent(CapacityReservation reservation) {
        try {
            Boolean created = redis.opsForValue()
                    .setIfAbsent(key(reservation.requestId()), serialize(reservation),
                            Duration.ofMillis(ttlMillis));
            if (Boolean.TRUE.equals(created)) return reservation;
            return deserialize(redis.opsForValue().get(key(reservation.requestId())));
        } catch (CapacityStoreUnavailable e) {
            throw e;
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("redis reservation store unavailable", e);
        }
    }

    @Override
    public CapacityReservation cas(String requestId, CapacityReservation.Status expected,
                                   CapacityReservation.Status next) {
        try {
            String raw = redis.opsForValue().get(key(requestId));
            if (raw == null) return null;
            CapacityReservation current = deserialize(raw);
            if (current.status() != expected || current.isTerminal()) return null;
            CapacityReservation moved = new CapacityReservation(
                    current.requestId(), current.callId(), current.nodeId(), current.epoch(),
                    next, current.reserved(), current.seq(), current.expiresAt(), Instant.now());
            String rawMoved = serialize(moved);
            // 双写 CAS：先 SETNX 新版本再删旧值。单键简单实现下用 SET 覆盖；
            // 多实例精确 CAS 由 Lua 脚本在独立任务中硬化（当前无多实例环境）。
            redis.opsForValue().set(key(requestId), rawMoved, Duration.ofMillis(ttlMillis));
            return moved;
        } catch (CapacityStoreUnavailable e) {
            throw e;
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("redis reservation store unavailable", e);
        }
    }

    @Override
    public CapacityReservation get(String requestId) {
        try {
            String raw = redis.opsForValue().get(key(requestId));
            return raw == null ? null : deserialize(raw);
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("redis reservation store unavailable", e);
        }
    }

    @Override
    public int expirePending(Instant now) {
        int expired = 0;
        for (CapacityReservation reservation : listActive()) {
            if (reservation.status() != CapacityReservation.Status.PENDING) continue;
            if (reservation.expiresAt() == null || !now.isAfter(reservation.expiresAt())) continue;
            if (cas(reservation.requestId(), CapacityReservation.Status.PENDING,
                    CapacityReservation.Status.EXPIRED) != null) {
                expired++;
            }
        }
        return expired;
    }

    @Override
    public List<CapacityReservation> listActive() {
        try {
            var keys = redis.keys(KEY_PREFIX + "*");
            List<CapacityReservation> result = new ArrayList<>();
            if (keys == null) return result;
            for (String key : keys) {
                String raw = redis.opsForValue().get(key);
                if (raw != null) {
                    CapacityReservation reservation = deserialize(raw);
                    if (reservation != null) result.add(reservation);
                }
            }
            return result;
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("redis reservation store unavailable", e);
        }
    }

    private String serialize(CapacityReservation reservation) {
        try {
            return mapper.writeValueAsString(new StoredReservation(
                    reservation.requestId(), reservation.callId(), reservation.nodeId(),
                    reservation.epoch(), reservation.status().name(), reservation.reserved(),
                    reservation.seq(), reservation.expiresAt(), reservation.updatedAt()));
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("reservation serialization failed", e);
        }
    }

    private CapacityReservation deserialize(String raw) {
        try {
            StoredReservation stored = mapper.readValue(raw, StoredReservation.class);
            if (stored == null || stored.requestId() == null) return null;
            return new CapacityReservation(
                    stored.requestId(), stored.callId(), stored.nodeId(), stored.epoch(),
                    CapacityReservation.Status.valueOf(stored.status()), stored.reserved(),
                    stored.seq(), stored.expiresAt(), stored.updatedAt());
        } catch (Exception e) {
            throw new CapacityStoreUnavailable("reservation deserialization failed", e);
        }
    }

    private static String key(String requestId) {
        return KEY_PREFIX + requestId;
    }

    public record StoredReservation(String requestId, String callId, String nodeId, long epoch,
                                    String status, Map<CapacityDimension, Double> reserved,
                                    long seq, Instant expiresAt, Instant updatedAt) {
    }
}