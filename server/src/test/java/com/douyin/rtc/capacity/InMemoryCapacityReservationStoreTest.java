package com.douyin.rtc.capacity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RTC-011 内存 reservation store 契约：同 requestId 幂等、CAS 收敛、
 * PENDING TTL 到期 EXPIRED；终态记录可被新 PENDING 覆盖（重放保障）。
 */
class InMemoryCapacityReservationStoreTest {

    private final InMemoryCapacityReservationStore store = new InMemoryCapacityReservationStore();

    private static CapacityReservation pending(String requestId, String callId) {
        return new CapacityReservation(requestId, callId, "node-a", 42L,
                CapacityReservation.Status.PENDING,
                Map.of(CapacityDimension.ROOMS, 1.0, CapacityDimension.PUBLISHERS, 1.0),
                0L, Instant.now().plusMillis(60_000), Instant.now());
    }

    @Test
    void createIsIdempotentByRequestId() {
        CapacityReservation first = store.createIfAbsent(pending("r1", "c1"));
        CapacityReservation second = store.createIfAbsent(pending("r1", "c100"));

        assertThat(second).isSameAs(first);
        assertThat(store.get("r1")).isSameAs(first);
    }

    @Test
    void terminalRecordIsOverwrittenByNewPending() {
        store.createIfAbsent(pending("r1", "c1"));
        store.cas("r1", CapacityReservation.Status.PENDING, CapacityReservation.Status.EXPIRED);

        CapacityReservation renewed = store.createIfAbsent(pending("r1", "c2"));

        assertThat(renewed.status()).isEqualTo(CapacityReservation.Status.PENDING);
        assertThat(renewed.callId()).isEqualTo("c2");
    }

    @Test
    void casMovesThroughLifecycleStates() {
        store.createIfAbsent(pending("r1", "c1"));

        assertThat(store.cas("r1", CapacityReservation.Status.PENDING,
                CapacityReservation.Status.CONSUMED).status()).isEqualTo(CapacityReservation.Status.CONSUMED);
        assertThat(store.cas("r1", CapacityReservation.Status.CONSUMED,
                CapacityReservation.Status.ABSORBED).status()).isEqualTo(CapacityReservation.Status.ABSORBED);
        // 终态后任意 CAS 都失败（null），不产生新推进
        assertThat(store.cas("r1", CapacityReservation.Status.ABSORBED,
                CapacityReservation.Status.RELEASED)).isNull();
    }

    @Test
    void casWithWrongExpectedReturnsNull() {
        store.createIfAbsent(pending("r1", "c1"));

        assertThat(store.cas("r1", CapacityReservation.Status.CONSUMED,
                CapacityReservation.Status.ABSORBED)).isNull();
        assertThat(store.get("r1").status()).isEqualTo(CapacityReservation.Status.PENDING);
    }

    @Test
    void expirePendingOnlyMaturesDuePendingToExpired() {
        Instant now = Instant.now();
        store.createIfAbsent(new CapacityReservation("r-due", "c1", "node-a", 42L,
                CapacityReservation.Status.PENDING,
                Map.of(CapacityDimension.ROOMS, 1.0), 0L, now.minusSeconds(1), now));
        store.createIfAbsent(new CapacityReservation("r-fresh", "c2", "node-a", 42L,
                CapacityReservation.Status.PENDING,
                Map.of(CapacityDimension.ROOMS, 1.0), 0L, now.plusSeconds(60), now));
        store.createIfAbsent(new CapacityReservation("r-consumed", "c3", "node-a", 42L,
                CapacityReservation.Status.CONSUMED,
                Map.of(CapacityDimension.ROOMS, 1.0), 0L, now.minusSeconds(1), now));

        int expired = store.expirePending(now);

        assertThat(expired).isEqualTo(1);
        assertThat(store.get("r-due").status()).isEqualTo(CapacityReservation.Status.EXPIRED);
        assertThat(store.get("r-fresh").status()).isEqualTo(CapacityReservation.Status.PENDING);
        assertThat(store.get("r-consumed").status()).isEqualTo(CapacityReservation.Status.CONSUMED);
    }

    @Test
    void listActiveReturnsAllRecords() {
        store.createIfAbsent(pending("r1", "c1"));
        store.createIfAbsent(pending("r2", "c2"));

        assertThat(store.listActive()).hasSize(2);
    }
}