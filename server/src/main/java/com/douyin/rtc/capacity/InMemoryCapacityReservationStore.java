package com.douyin.rtc.capacity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本机开发/测试用内存 store。语义与 Redis 实现一致：
 * 同 requestId 幂等、CAS 收敛、PENDING TTL 到期 EXPIRED。
 */
public class InMemoryCapacityReservationStore implements CapacityReservationStore {

    private final ConcurrentHashMap<String, CapacityReservation> store = new ConcurrentHashMap<>();

    @Override
    public CapacityReservation createIfAbsent(CapacityReservation reservation) {
        return store.compute(reservation.requestId(), (key, current) -> {
            if (current != null && !current.isTerminal()) return current;
            return reservation;
        });
    }

    @Override
    public CapacityReservation cas(String requestId, CapacityReservation.Status expected,
                                   CapacityReservation.Status next) {
        AtomicReference<CapacityReservation> result = new AtomicReference<>();
        store.computeIfPresent(requestId, (key, current) -> {
            if (current.status() != expected || current.isTerminal()) {
                return current; // 状态不匹配或已是终态：原地不动且不返回“成功”
            }
            CapacityReservation moved = new CapacityReservation(
                    current.requestId(), current.callId(), current.nodeId(), current.epoch(),
                    next, current.reserved(), current.seq(), current.expiresAt(), Instant.now());
            result.set(moved);
            return moved;
        });
        return result.get();
    }

    @Override
    public CapacityReservation get(String requestId) {
        return store.get(requestId);
    }

    @Override
    public int expirePending(Instant now) {
        int expired = 0;
        for (Map.Entry<String, CapacityReservation> entry : store.entrySet()) {
            CapacityReservation current = entry.getValue();
            if (current.status() != CapacityReservation.Status.PENDING) continue;
            if (current.expiresAt() == null || !now.isAfter(current.expiresAt())) continue;
            if (cas(entry.getKey(), CapacityReservation.Status.PENDING,
                    CapacityReservation.Status.EXPIRED) != null) {
                expired++;
            }
        }
        return expired;
    }

    @Override
    public List<CapacityReservation> listActive() {
        return new ArrayList<>(store.values());
    }
}