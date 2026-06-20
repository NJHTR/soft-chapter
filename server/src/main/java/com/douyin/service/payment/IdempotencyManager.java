package com.douyin.service.payment;

import com.douyin.service.RedisCacheService;
import org.springframework.stereotype.Component;

/**
 * 幂等性管理器 — 基于 Redis SETNX 的分布式幂等锁。
 */
@Component
public class IdempotencyManager {

    private final RedisCacheService cache;

    public IdempotencyManager(RedisCacheService cache) {
        this.cache = cache;
    }

    /**
     * 尝试获取幂等锁
     * @return true=首次请求, false=重复请求
     */
    public boolean tryAcquire(String idempotencyKey, Long userId, Long orderId) {
        return cache.tryAcquireIdempotent(idempotencyKey);
    }

    /** 释放幂等锁 (Redis TTL 自动过期，无需手动释放) */
    public void release(String idempotencyKey) {
        // Redis 的 SETNX + EXPIRE 自动过期，保留记录防止窗口期重放
    }

    /** 过期记录由 Redis TTL 自动清理，无需定时任务 */
    public void cleanExpired(long ttlMs) {
        // no-op: Redis 自动过期
    }
}
