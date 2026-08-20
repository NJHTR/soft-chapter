package com.douyin.rtc.capacity;

import java.time.Instant;
import java.util.Map;

/**
 * 节点容量观测快照。sampledAt 用于 stale 判定：
 * snapshot 缺失、stale 或不可信时新房 fail-closed。
 */
public record CapacitySnapshot(
        String nodeId,
        long epoch,
        Instant sampledAt,
        Map<CapacityDimension, Double> observed
) {
    public static CapacitySnapshot stale(String nodeId, long epoch) {
        return new CapacitySnapshot(nodeId, epoch, Instant.EPOCH, Map.of());
    }
}