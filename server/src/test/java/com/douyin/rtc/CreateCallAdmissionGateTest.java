package com.douyin.rtc;

import com.douyin.rtc.capacity.AdmissionService;
import com.douyin.rtc.capacity.CapacityDimension;
import com.douyin.rtc.capacity.CapacityNodeConfig;
import com.douyin.rtc.capacity.CapacityProperties;
import com.douyin.rtc.capacity.CapacityRegistry;
import com.douyin.rtc.capacity.CapacityReservation;
import com.douyin.rtc.capacity.CapacityReservationStore;
import com.douyin.rtc.capacity.InMemoryCapacityReservationStore;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.service.AclService;
import com.douyin.rtc.service.CallLedgerService;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CreateCallCommand;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RTC-011 新房 admission 门禁在 create 路径上的行为：
 * - 开启 + 容量充足 → 放行并留下 PENDING reservation
 * - 容量已满 → CAPACITY_REJECTED，不落库
 * - store 不可用 → fail-closed CAPACITY_REJECTED
 * - 落库失败 → reservation 立即 RELEASED（异常回滚不吸收）
 */
class CreateCallAdmissionGateTest {

    private static final String NODE = "node-a";
    private static final long EPOCH = 1L;

    private RtcRepoFixture fx;
    private CapacityRegistry registry;
    private InMemoryCapacityReservationStore store;
    private AdmissionService admission;
    private CapacityProperties props;

    @BeforeEach
    void setUp() {
        fx = new RtcRepoFixture();
        CapacityNodeConfig node = new CapacityNodeConfig();
        node.setNodeId(NODE);
        node.setLimits(Map.of(CapacityDimension.ROOMS, 2.0,
                CapacityDimension.PUBLISHERS, 20.0));
        props = new CapacityProperties();
        props.setAdmissionEnabled(true);
        props.setNodes(List.of(node));
        registry = new CapacityRegistry(15_000L);
        store = new InMemoryCapacityReservationStore();
        admission = new AdmissionService(props, registry, store);
        // 默认喂养一次观测，避免空 registry 误伤
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 1);
        registry.record(NODE, EPOCH, CapacityDimension.PUBLISHERS, 2);
    }

    private CallService gatedService() {
        return new CallService(fx.sessions, fx.participants, fx.events,
                new AclService(fx.acl),
                new CallLedgerService(fx.events, fx.projection),
                fx.redis,
                fx.constRingingTtl(), fx.constNegotiatingTtl(), admission);
    }

    private CallService flatService() {
        return CallTestSupport.service(fx);
    }

    @Test
    void allowedWhenCapacityAvailable() {
        fx.setMutualFollow(CallTestSupport.INITIATOR, CallTestSupport.CALLEE);

        CallService svc = gatedService();
        var session = svc.createCall(CallTestSupport.directCommand(
                CallTestSupport.INITIATOR, CallTestSupport.CALLEE, "creq-allow", "evt-allow-1"));

        assertThat(session.getState()).isEqualTo("RINGING");
        assertThat(store.get("create:creq-allow")).isNotNull();
        assertThat(store.get("create:creq-allow").status()).isEqualTo(CapacityReservation.Status.PENDING);
    }

    @Test
    void rejectWhenRoomAtHardLimit() {
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 2); // 2/2 = 1.0 ≥ 0.8
        fx.setMutualFollow(CallTestSupport.INITIATOR, CallTestSupport.CALLEE);
        CallService svc = gatedService();

        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                CallTestSupport.INITIATOR, CallTestSupport.CALLEE, "creq-reject", "evt-reject-1")))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.CAPACITY_REJECTED);

        assertThat(fx.sessionsByClientRequest).doesNotContainKeys("creq-reject");
    }

    @Test
    void gatedCreateDoesNotTouchNonGatedPath() {
        // 未开启 admission 的构造走旧 8 参构造（admission=null），完全不插桩容量
        fx.setMutualFollow(CallTestSupport.INITIATOR, CallTestSupport.CALLEE);
        CallService svc = flatService();

        var session = svc.createCall(CallTestSupport.directCommand(
                CallTestSupport.INITIATOR, CallTestSupport.CALLEE, "creq-flat", "evt-flat-1"));

        assertThat(session.getState()).isEqualTo("RINGING");
    }

    @Test
    void storeUnavailableFailsClosed() {
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
            @Override public int expirePending(java.time.Instant now) {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
            @Override public List<CapacityReservation> listActive() {
                throw new CapacityReservationStore.CapacityStoreUnavailable("redis down");
            }
        };
        AdmissionService downAdmission = new AdmissionService(props, registry, down);
        fx.setMutualFollow(CallTestSupport.INITIATOR, CallTestSupport.CALLEE);
        CallService svc = new CallService(fx.sessions, fx.participants, fx.events,
                new AclService(fx.acl), new CallLedgerService(fx.events, fx.projection),
                fx.redis, fx.constRingingTtl(), fx.constNegotiatingTtl(), downAdmission);

        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                CallTestSupport.INITIATOR, CallTestSupport.CALLEE, "creq-down", "evt-down-1")))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.CAPACITY_REJECTED);
    }

    @Test
    void insertFailureReleasesReservation() {
        registry.record(NODE, EPOCH, CapacityDimension.ROOMS, 1);
        when(fx.sessions.insert(any(com.douyin.rtc.domain.CallSession.class)))
                .thenThrow(new RuntimeException("db down"));
        fx.setMutualFollow(CallTestSupport.INITIATOR, CallTestSupport.CALLEE);
        CallService svc = gatedService();

        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                CallTestSupport.INITIATOR, CallTestSupport.CALLEE, "creq-fail", "evt-fail-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");

        assertThat(store.get("create:creq-fail").status()).isEqualTo(CapacityReservation.Status.RELEASED);
    }
}