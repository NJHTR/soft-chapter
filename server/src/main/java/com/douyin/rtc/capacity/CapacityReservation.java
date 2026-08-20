package com.douyin.rtc.capacity;

import java.time.Instant;
import java.util.Map;

/**
 * 容量 reservation：按维（observed + reserved → remaining）逐维吸收的幂等记录。
 * 生命周期：PENDING -> CONSUMED -> ABSORBED | RELEASED | EXPIRED。
 */
public record CapacityReservation(
        String requestId,
        String callId,
        String nodeId,
        long epoch,
        Status status,
        Map<CapacityDimension, Double> reserved,
        long seq,
        Instant expiresAt,
        Instant updatedAt
) {
    public enum Status {
        PENDING, CONSUMED, ABSORBED, RELEASED, EXPIRED
    }

    public boolean isTerminal() {
        return status == Status.ABSORBED || status == Status.RELEASED || status == Status.EXPIRED;
    }
}