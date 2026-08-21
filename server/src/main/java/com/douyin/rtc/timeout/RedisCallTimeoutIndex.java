package com.douyin.rtc.timeout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Redis ZSET timeout index sharded by call id hash. */
@Component
public class RedisCallTimeoutIndex implements CallTimeoutIndex {

    private static final String KEY_PREFIX = "douyin:rtc:timeout:";
    private static final String SEPARATOR = "|";

    private final RedisTemplate<String, Object> redis;
    private final int shards;

    public RedisCallTimeoutIndex(RedisTemplate<String, Object> redis,
                                 @Value("${rtc.call.timeout-shards:32}") int shards) {
        this.redis = redis;
        this.shards = Math.max(1, Math.min(256, shards));
    }

    @Override
    public void schedule(String callId, String expectedState, Instant expireAt) {
        validate(callId, expectedState, expireAt);
        try {
            redis.opsForZSet().remove(key(callId),
                    "RINGING" + SEPARATOR + callId,
                    "NEGOTIATING" + SEPARATOR + callId);
            redis.opsForZSet().add(key(callId), member(callId, expectedState), expireAt.toEpochMilli());
        } catch (Exception e) {
            throw new CallTimeoutIndexUnavailable("timeout index schedule failed", e);
        }
    }

    @Override
    public void remove(String callId) {
        if (callId == null || callId.isBlank()) {
            return;
        }
        try {
            redis.opsForZSet().remove(key(callId),
                    "RINGING" + SEPARATOR + callId,
                    "NEGOTIATING" + SEPARATOR + callId);
        } catch (Exception e) {
            throw new CallTimeoutIndexUnavailable("timeout index remove failed", e);
        }
    }

    @Override
    public List<DueTimeout> due(Instant now, int limit) {
        int boundedLimit = Math.max(1, Math.min(2000, limit));
        int perShard = Math.max(1, (boundedLimit + shards - 1) / shards);
        List<DueTimeout> result = new ArrayList<>(boundedLimit);
        try {
            for (int shard = 0; shard < shards && result.size() < boundedLimit; shard++) {
                Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> members =
                        redis.opsForZSet().rangeByScoreWithScores(
                                key(shard), Double.NEGATIVE_INFINITY, now.toEpochMilli(), 0, perShard);
                if (members == null) {
                    continue;
                }
                for (var tuple : members) {
                    DueTimeout parsed = parse(String.valueOf(tuple.getValue()),
                            tuple.getScore() == null ? now : Instant.ofEpochMilli(tuple.getScore().longValue()));
                    if (parsed != null) {
                        result.add(parsed);
                        if (result.size() >= boundedLimit) {
                            break;
                        }
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new CallTimeoutIndexUnavailable("timeout index read failed", e);
        }
    }

    private void validate(String callId, String expectedState, Instant expireAt) {
        if (callId == null || callId.isBlank() || callId.contains(SEPARATOR)
                || expectedState == null || expectedState.isBlank() || expectedState.contains(SEPARATOR)
                || expireAt == null) {
            throw new IllegalArgumentException("invalid call timeout entry");
        }
    }

    private String member(String callId, String expectedState) {
        return expectedState + SEPARATOR + callId;
    }

    private DueTimeout parse(String member, Instant scoreFallback) {
        int split = member.indexOf(SEPARATOR);
        if (split <= 0 || split == member.length() - 1) {
            return null;
        }
        return new DueTimeout(member.substring(split + 1), member.substring(0, split), scoreFallback);
    }

    private String key(String callId) {
        return key(Math.floorMod(callId.hashCode(), shards));
    }

    private String key(int shard) {
        return KEY_PREFIX + String.format("%02x", shard);
    }
}
