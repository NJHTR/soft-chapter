package com.douyin.rtc.capacity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RTC-011 admission 决策契约：
 * - 关闭/未配置节点/无新鲜快照 → 按契约 fail-closed（REJECT，绝不推算容量）
 * - ratio ≥ hard(0.8) → REJECT；0.7..0.8 → DEGRADE_VIDEO
 * - reservation PENDING -> CONSUMED -> ABSORBED，只影响新房/首次 token
 * - store 不可用且无新鲜缓存 → fail-closed（抛 CapacityStoreUnavailable）
 */
class AdmissionServiceTest {

    private static final String NODE = "node-a";
    private static final long EPOCH = 42L;

    private static CapacityProperties properties(CapacityNodeConfig node) {
        CapacityProperties props = new CapacityProperties();
        props.setAdmissionEnabled(true);
        props.setNodes(List.of(node));
        props.setHardThreshold(0.8);
        props.setWarnThreshold(0.7);
        props.setSnapshotStaleAfterMillis(15_000L);
        props.setReservationTtlMillis(60_000L);
        return props;
    }

    private static CapacityNodeConfig node(double roomsLimit, double publishersLimit) {
        CapacityNodeConfig node = new CapacityNodeConfig();
        node.setNodeId(NODE);
        node.setLimits(Map.of(
                CapacityDimension.ROOMS, roomsLimit,
                CapacityDimension.PUBLISHERS, publishersLimit));
        return node;
    }

    private static AdmissionService allowAllService() {
        CapacityProperties props = new CapacityProperties();
        props.setAdmissionEnabled(true);
        props.setNodes(List.of(node(100, 200)));
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 1);
        registry.record(NODE, EPOCH, CapacityDimension.PUBLISHERS, 2);
        return new AdmissionService(props, registry, new InMemoryCapacityReservationStore());
    }

    @Test
    void disabledFeatureAlwaysAllows() {
        CapacityProperties props = properties(node(10, 10));
        props.setAdmissionEnabled(false);
        AdmissionService service = new AdmissionService(props,
                new CapacityRegistry(15_000L), new InMemoryCapacityReservationStore());

        AdmissionDecision decision = service.evaluate(NODE, EPOCH, "call-1", "req-1");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason().code()).isEqualTo("feature_disabled");
    }

    @Test
    void unknownNodeRejects() {
        AdmissionService service = allowAllService();

        AdmissionDecision decision = service.evaluate("ghost", EPOCH, "call-1", "req-1");

        assertThat(decision.decision()).isEqualTo(AdmissionDecision.Decision.REJECT_NEW_ROOM);
        assertThat(decision.reason().code()).isEqualTo("unknown_node");
    }

    @Test
    void missingOrStaleSnapshotRejectsFailClosed() {
        AdmissionService service = allowAllService();

        // 没有观测数据的节点（空 registry 或 epoch 不匹配）一律 fail-closed
        AdmissionDecision stale = service.evaluate(NODE, EPOCH + 1, "call-1", "req-1");

        assertThat(stale.decision()).isEqualTo(AdmissionDecision.Decision.REJECT_NEW_ROOM);
        assertThat(stale.reason().code()).isEqualTo("stale_or_missing_snapshot");
    }

    @Test
    void underWarnThresholdAllows() {
        AdmissionService service = allowAllService();

        AdmissionDecision decision = service.evaluate(NODE, EPOCH, "call-1", "req-1");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.decision()).isEqualTo(AdmissionDecision.Decision.ALLOW);
        assertThat(decision.reason().code()).isEqualTo("ok");
    }

    @Test
    void hardThresholdRejectsWithDimension() {
        CapacityProperties props = properties(node(10, 100));
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 8);
        AdmissionService service = new AdmissionService(props, registry,
                new InMemoryCapacityReservationStore());

        AdmissionDecision decision = service.evaluate(NODE, EPOCH, "call-1", "req-1");

        assertThat(decision.decision()).isEqualTo(AdmissionDecision.Decision.REJECT_NEW_ROOM);
        assertThat(decision.reason().code()).isEqualTo("over_capacity");
        assertThat(decision.reason().dimension()).isEqualTo("rooms");
    }

    @Test
    void degradeOnlyWhenAllDimsInWarnBand() {
        CapacityProperties props = properties(node(100, 10));
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 75);      // 0.75
        registry.record(NODE, EPOCH, CapacityDimension.PUBLISHERS, 5);  // 0.5
        AdmissionService service = new AdmissionService(props, registry,
                new InMemoryCapacityReservationStore());

        AdmissionDecision decision = service.evaluate(NODE, EPOCH, "call-1", "req-1");

        assertThat(decision.decision()).isEqualTo(AdmissionDecision.Decision.DEGRADE_VIDEO);
    }

    @Test
    void reserveIsIdempotentAndDoesNotConsume() {
        AdmissionService service = allowAllService();

        CapacityReservation first = service.reserve("req-1", "call-1", NODE, EPOCH,
                Map.of(CapacityDimension.ROOMS, 1.0), Instant.now());
        CapacityReservation second = service.reserve("req-1", "call-1", NODE, EPOCH,
                Map.of(CapacityDimension.ROOMS, 1.0), Instant.now());

        assertThat(second).isSameAs(first);
        assertThat(first.status()).isEqualTo(CapacityReservation.Status.PENDING);
    }

    @Test
    void consumeThenAbsorbFeedsRegistryObserved() {
        AdmissionService service = allowAllService();
        service.reserve("req-1", "call-1", NODE, EPOCH,
                Map.of(CapacityDimension.PUBLISHERS, 2.0), Instant.now());

        CapacityReservation consumed = service.consume("req-1");
        assertThat(consumed.status()).isEqualTo(CapacityReservation.Status.CONSUMED);

        CapacityReservation absorbed = service.absorb("req-1", 1L);
        assertThat(absorbed.status()).isEqualTo(CapacityReservation.Status.ABSORBED);
        assertThat(service.registry().snapshot(NODE, EPOCH).observed())
                .containsEntry(CapacityDimension.PUBLISHERS, 4.0); // 2 observed + 2 absorbed
        assertThat(service.registry().seqFloor(NODE, EPOCH)).isEqualTo(1L);

        // 重复 absorb 幂等（已终态），seq 不重复累计
        service.absorb("req-1", 2L);
        assertThat(service.registry().snapshot(NODE, EPOCH).observed())
                .containsEntry(CapacityDimension.PUBLISHERS, 4.0);
    }

    @Test
    void consumeOnNotFoundAndReplayIsSafe() {
        AdmissionService service = allowAllService();

        assertThat(service.consume("missing")).isNull();
        assertThat(service.absorb("missing", 1L)).isNull();

        service.reserve("req-1", "call-1", NODE, EPOCH,
                Map.of(CapacityDimension.ROOMS, 1.0), Instant.now());
        assertThat(service.release("req-1").status()).isEqualTo(CapacityReservation.Status.RELEASED);
        // release 幂等：再次 release 返回已 RELEASED，不抛异常
        assertThat(service.release("req-1").status()).isEqualTo(CapacityReservation.Status.RELEASED);
    }

    @Test
    void evaluateCountsPendingReservationsAgainstRemaining() {
        CapacityProperties props = properties(node(10, 100));
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 8);
        AdmissionService service = new AdmissionService(props, registry,
                new InMemoryCapacityReservationStore());

        // 8/10 = 0.8 → 已满；pending reservation 阈值不因 reserve 而改变，直接拒绝
        assertThat(service.evaluate(NODE, EPOCH, "call-9", "req-9")
                .decision()).isEqualTo(AdmissionDecision.Decision.REJECT_NEW_ROOM);
    }

    @Test
    void storeUnavailableFailCloses() {
        CapacityProperties props = properties(node(100, 100));
        CapacityRegistry registry = new CapacityRegistry(15_000L);
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 1);
        CapacityReservationStore down = new CapacityReservationStore() {
            @Override public CapacityReservation createIfAbsent(CapacityReservation r) {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
            @Override public CapacityReservation cas(String r, CapacityReservation.Status s,
                                                     CapacityReservation.Status n) {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
            @Override public CapacityReservation get(String requestId) {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
            @Override public int expirePending(Instant now) {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
            @Override public List<CapacityReservation> listActive() {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
        };
        AdmissionService service = new AdmissionService(props, registry, down);

        assertThatThrownBy(() -> service.evaluate(NODE, EPOCH, "call-1", "req-1"))
                .isInstanceOf(CapacityReservationStore.CapacityStoreUnavailable.class);
    }
}