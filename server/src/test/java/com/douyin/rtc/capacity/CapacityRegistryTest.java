package com.douyin.rtc.capacity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RTC-011 节点观测 registry 契约：epoch rebind 重置 observed 与 seq floor；
 * 旧世代凭据吸收一律丢弃；每次 record 都更新新鲜窗口。
 */
class CapacityRegistryTest {

    @Test
    void recordThenSnapshotIsFresh() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);

        registry.record("node-a", 42L, CapacityDimension.ROOMS, 3);
        registry.record("node-a", 42L, CapacityDimension.PUBLISHERS, 7);

        CapacitySnapshot snapshot = registry.snapshot("node-a", 42L);
        assertThat(snapshot.sampledAt()).isNotEqualTo(Instant.EPOCH);
        assertThat(snapshot.observed()).containsEntry(CapacityDimension.ROOMS, 3.0);
        assertThat(snapshot.observed()).containsEntry(CapacityDimension.PUBLISHERS, 7.0);
    }

    @Test
    void unknownNodeSnapshotIsStale() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);

        CapacitySnapshot snapshot = registry.snapshot("missing", 1L);

        assertThat(snapshot.sampledAt()).isEqualTo(Instant.EPOCH);
        assertThat(snapshot.observed()).isEmpty();
    }

    @Test
    void epochMismatchYieldsStaleSnapshotEvenIfObserved() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.ROOMS, 5);

        // 调用方拿着旧世代 epoch 查询 → 不做推算，fail-closed
        assertThat(registry.snapshot("node-a", 41L).sampledAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void epochRebindResetsObservedAndSeqFloor() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.ROOMS, 5);
        registry.absorbObserved("node-a", 42L, 7L,
                Map.of(CapacityDimension.ROOMS, 1.0));
        assertThat(registry.snapshot("node-a", 42L).observed())
                .containsEntry(CapacityDimension.ROOMS, 6.0);

        registry.record("node-a", 99L, CapacityDimension.ROOMS, 2);

        assertThat(registry.snapshot("node-a", 99L).observed())
                .containsOnly(Map.entry(CapacityDimension.ROOMS, 2.0));
        assertThat(registry.seqFloor("node-a", 99L)).isZero();
    }

    @Test
    void absorbAppliesDeltasAndAdvancesSeqFloor() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.PUBLISHERS, 3);

        registry.absorbObserved("node-a", 42L, 5L,
                Map.of(CapacityDimension.PUBLISHERS, 2.0));

        assertThat(registry.snapshot("node-a", 42L).observed())
                .containsEntry(CapacityDimension.PUBLISHERS, 5.0);
        assertThat(registry.seqFloor("node-a", 42L)).isEqualTo(5L);
    }

    @Test
    void staleOrReorderedAbsorbSeqIsDropped() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.PUBLISHERS, 1);
        registry.absorbObserved("node-a", 42L, 8L,
                Map.of(CapacityDimension.PUBLISHERS, 1.0));

        registry.absorbObserved("node-a", 42L, 8L, Map.of(CapacityDimension.PUBLISHERS, 1.0));
        registry.absorbObserved("node-a", 42L, 3L, Map.of(CapacityDimension.PUBLISHERS, 1.0));

        assertThat(registry.snapshot("node-a", 42L).observed())
                .containsEntry(CapacityDimension.PUBLISHERS, 2.0);
        assertThat(registry.seqFloor("node-a", 42L)).isEqualTo(8L);
    }

    @Test
    void absorbFromOldEpochRebindsInsteadOfAccumulating() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.ROOMS, 5);

        // 旧世代凭据在新世代(99)下出现：不是累计过去用量，而是从凭证落地世代重算。
        registry.absorbObserved("node-a", 99L, 1L,
                Map.of(CapacityDimension.ROOMS, 2.0));

        assertThat(registry.snapshot("node-a", 99L).observed())
                .containsOnly(Map.entry(CapacityDimension.ROOMS, 2.0));
        assertThat(registry.seqFloor("node-a", 99L)).isEqualTo(1L);
    }

    @Test
    void epochAccessorReturnsUnknownForMissingNode() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        assertThat(registry.epoch("missing")).isEqualTo(-1L);

        registry.record("node-a", 42L, CapacityDimension.ROOMS, 1);
        assertThat(registry.epoch("node-a")).isEqualTo(42L);
    }

    @Test
    void zeroOrNegativeObservationIsIgnored() {
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record("node-a", 42L, CapacityDimension.ROOMS, 4);

        registry.record("node-a", 42L, CapacityDimension.ROOMS, 0);
        registry.record("node-a", 42L, CapacityDimension.ROOMS, -1);

        assertThat(registry.snapshot("node-a", 42L).observed())
                .containsExactly(Map.entry(CapacityDimension.ROOMS, 4.0));
    }
}