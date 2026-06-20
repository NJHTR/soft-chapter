package com.douyin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 统一缓存服务 — 热点数据缓存 / 限流 / 排行榜 / 分布式锁
 *
 * Key 命名规范: douyin:{category}:{sub}:{id}
 */
@Slf4j
@Service
public class RedisCacheService {

    private final RedisTemplate<String, Object> redis;

    private static final String PREFIX_VIDEO = "douyin:video:meta:";
    private static final String PREFIX_USER = "douyin:user:profile:";
    private static final String PREFIX_RECOMMEND = "douyin:recommend:feed:";
    private static final String PREFIX_HOTWORDS = "douyin:search:hotwords";
    private static final String PREFIX_RATE_LIMIT = "douyin:ratelimit:";
    private static final String PREFIX_IDEMPOTENT = "douyin:idempotent:";
    private static final String PREFIX_LOCK = "douyin:lock:";

    private static final Duration TTL_VIDEO = Duration.ofMinutes(30);
    private static final Duration TTL_USER = Duration.ofMinutes(10);
    private static final Duration TTL_RECOMMEND = Duration.ofMinutes(5);
    private static final Duration TTL_IDEMPOTENT = Duration.ofMinutes(30);

    public RedisCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    // ==================== 通用缓存 ====================

    public Optional<Object> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Redis GET failed key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis PUT failed key={}: {}", key, e.getMessage());
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL failed key={}: {}", key, e.getMessage());
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            var keys = new java.util.HashSet<String>();
            try (var cursor = redis.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern).count(100).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            log.warn("Redis DEL pattern={} failed: {}", pattern, e.getMessage());
        }
    }

    // ==================== 视频元数据缓存 ====================

    public Optional<Object> getVideoMeta(Long videoId) {
        return get(PREFIX_VIDEO + videoId);
    }

    public void putVideoMeta(Long videoId, Object meta) {
        put(PREFIX_VIDEO + videoId, meta, TTL_VIDEO);
    }

    /** 批量获取视频元数据——缓存命中率最大化 */
    public Map<Long, Object> getVideoMetas(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        List<String> keys = videoIds.stream().map(id -> PREFIX_VIDEO + id).toList();
        try {
            List<Object> values = redis.opsForValue().multiGet(keys);
            if (values == null) return Map.of();
            Map<Long, Object> result = new HashMap<>();
            for (int i = 0; i < videoIds.size(); i++) {
                if (values.get(i) != null) result.put(videoIds.get(i), values.get(i));
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis MGET videos failed: {}", e.getMessage());
            return Map.of();
        }
    }

    // ==================== 用户资料缓存 ====================

    public Optional<Object> getUserProfile(Long userId) {
        return get(PREFIX_USER + userId);
    }

    public void putUserProfile(Long userId, Object profile) {
        put(PREFIX_USER + userId, profile, TTL_USER);
    }

    // ==================== 推荐结果缓存 ====================

    /** 缓存用户推荐结果: key = userId + pageNum */
    public Optional<Object> getRecommendFeed(Long userId, int pageNum) {
        return get(PREFIX_RECOMMEND + userId + ":" + pageNum);
    }

    public void putRecommendFeed(Long userId, int pageNum, List<Long> videoIds) {
        put(PREFIX_RECOMMEND + userId + ":" + pageNum, (Object) videoIds, TTL_RECOMMEND);
    }

    /** 用户有新行为(点赞/收藏/关注)时失效其推荐缓存 */
    public void invalidateRecommend(Long userId) {
        deleteByPattern(PREFIX_RECOMMEND + userId + ":*");
    }

    // ==================== 搜索热词 (ZSet) ====================

    /** 记录一次搜索，score 自增 */
    public void recordSearchKeyword(String keyword) {
        try {
            redis.opsForZSet().incrementScore(PREFIX_HOTWORDS, keyword.toLowerCase(), 1);
        } catch (Exception e) {
            log.warn("Redis ZINCR hotwords failed: {}", e.getMessage());
        }
    }

    /** 获取 Top N 热词 */
    public List<String> getHotSearchKeywords(int n) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> set =
                    redis.opsForZSet().reverseRangeWithScores(PREFIX_HOTWORDS, 0, n - 1);
            if (set == null) return List.of();
            return set.stream()
                    .map(t -> t.getValue() != null ? t.getValue().toString() : null)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Redis ZREVRANGE hotwords failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 清理3天前的热词 (定时任务调用) */
    public void pruneStaleHotwords() {
        try {
            // 降权: 所有词 score 乘以衰减因子
            redis.opsForZSet().removeRangeByScore(PREFIX_HOTWORDS, 0, 0.1);
        } catch (Exception ignored) {}
    }

    // ==================== 限流 ====================

    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            if current > limit then
                return 0
            end
            return 1
            """;

    /**
     * 滑动窗口限流（简化版: 固定时间窗口）
     * @return true=放行, false=限流
     */
    public boolean rateLimit(String action, String identifier, int maxRequests, Duration window) {
        try {
            String key = PREFIX_RATE_LIMIT + action + ":" + identifier;
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
            Long result = redis.execute(script, List.of(key),
                    String.valueOf(maxRequests), String.valueOf(window.getSeconds()));
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("Rate limit check failed: action={} id={}", action, identifier, e);
            return true; // Redis 不可用时放行
        }
    }

    // ==================== 幂等锁 (替代 IdempotencyManager) ====================

    /**
     * 尝试获取幂等锁
     * @return true=首次请求, false=重复请求
     */
    public boolean tryAcquireIdempotent(String idempotencyKey) {
        try {
            String key = PREFIX_IDEMPOTENT + idempotencyKey;
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", TTL_IDEMPOTENT);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("Idempotent lock failed key={}: {}", idempotencyKey, e.getMessage());
            return true; // Redis 不可用时放行，避免阻塞正常业务
        }
    }

    // ==================== 分布式锁 (简单版) ====================

    /**
     * 获取分布式锁
     * @return lock token (用于释放), null=获取失败
     */
    public String tryLock(String resource, Duration ttl) {
        try {
            String key = PREFIX_LOCK + resource;
            String token = UUID.randomUUID().toString();
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception e) {
            log.warn("Distributed lock failed resource={}: {}", resource, e.getMessage());
            return UUID.randomUUID().toString(); // Redis 不可用时返回假 token，不阻塞
        }
    }

    /** 释放分布式锁 (带 token 校验防止误删) */
    public boolean unlock(String resource, String token) {
        String script = """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """;
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
            Long result = redis.execute(redisScript, List.of(PREFIX_LOCK + resource), token);
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("Unlock failed resource={}: {}", resource, e.getMessage());
            return false;
        }
    }
}
