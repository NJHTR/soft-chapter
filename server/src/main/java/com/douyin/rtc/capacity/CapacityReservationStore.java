package com.douyin.rtc.capacity;

import java.util.List;

/**
 * reservation 存储端口。实现必须允许 CAS 收敛（同 requestId 幂等），
 * Redis 不可用或异常时必须抛异常由 AdmissionService fail-closed。
 */
public interface CapacityReservationStore {

    /**
     * 幂等创建 PENDING；同 requestId 已存在时返回既有记录。
     *
     * @throws CapacityStoreUnavailable 存储不可用（fail-closed 信号）
     */
    CapacityReservation createIfAbsent(CapacityReservation reservation);

    /** CAS: 仅当当前状态为 expected 时迁移到 next；失败返回 null（不抛异常）。 */
    CapacityReservation cas(String requestId, CapacityReservation.Status expected,
                            CapacityReservation.Status next);

    CapacityReservation get(String requestId);

    /** 把过期的 PENDING 收敛为 EXPIRED；返回本次推进的数量。 */
    int expirePending(java.time.Instant now);

    /** low-cardinality 状态计数快照，供指标/诊断；键为状态名。 */
    List<CapacityReservation> listActive();

    /** 存储不可用时的 fail-closed 信号。 */
    class CapacityStoreUnavailable extends RuntimeException {
        public CapacityStoreUnavailable(String message, Throwable cause) {
            super(message, cause);
        }

        public CapacityStoreUnavailable(String message) {
            super(message);
        }
    }
}