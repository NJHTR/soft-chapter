package com.douyin.rtc.stage;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StageService 幂等/CAS/名额/ACL/provider 收敛测试(纯 JUnit5 + Mockito,不启动 Spring)。
 */
class StageServiceTest {

    private static final long HOST = 100L;
    private static final long ALICE = 1L;
    private static final long BOB = 2L;
    private static final long LIVE_ID = 9001L;

    private StageStore store;
    private StageProviderPort provider;
    private StageProperties props;
    private StageService svc;

    @BeforeEach
    void setUp() {
        store = new InMemoryStageStore();
        provider = mock(StageProviderPort.class);
        props = new StageProperties();
        props.setCapPublishers(8);
        props.setHostUserIds(List.of(HOST));
        svc = new StageService(store, provider, props);
    }

    private static StageEvent ev(StageCommand command, long liveId, long userId, long actor,
                                 String eventId, long generation) {
        return new StageEvent(command, liveId, userId, actor, eventId, generation, 1_700_000_000_000L);
    }

    @Test
    void replayedEventReturnsFirstResultAndDoesNotReapply() {
        StageMember first = svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, ALICE, "ev-1", 0));
        assertThat(first.status()).isEqualTo(StageMemberStatus.REQUESTED);
        assertThat(first.generation()).isEqualTo(1);

        StageMember replay = svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, ALICE, "ev-1", 0));
        assertThat(replay.generation()).isEqualTo(1);
        assertThat(replay).isSameAs(first);
        assertThat(store.audit(LIVE_ID, 100)).hasSize(1);
    }

    @Test
    void staleGenerationIsRejectedAndCannotResurrectMember() {
        svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, ALICE, "ev-1", 0));
        assertThatThrownBy(() -> svc.apply(ev(StageCommand.APPROVE, LIVE_ID, ALICE, HOST, "ev-2", 0)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.GENERATION_STALE));
        assertThat(store.find(LIVE_ID, ALICE).status()).isEqualTo(StageMemberStatus.REQUESTED);
    }

    @Test
    void requestByAnotherUserIsRejected() {
        assertThatThrownBy(() -> svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, BOB, "ev-x", 0)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_TARGET));
    }

    @Test
    void approveRequiresHost() {
        svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, ALICE, "ev-1", 0));
        assertThatThrownBy(() -> svc.apply(ev(StageCommand.APPROVE, LIVE_ID, ALICE, BOB, "ev-2", 1)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void fullLifecycleRequestApproveJoinedDemoteLeft() {
        svc.apply(ev(StageCommand.REQUEST, LIVE_ID, ALICE, ALICE, "ev-1", 0));
        svc.apply(ev(StageCommand.APPROVE, LIVE_ID, ALICE, HOST, "ev-2", 1));
        StageMember onStage = svc.apply(ev(StageCommand.JOINED, LIVE_ID, ALICE, HOST, "ev-3", 2));
        assertThat(onStage.status()).isEqualTo(StageMemberStatus.ON_STAGE);
        assertThat(onStage.mayPublish()).isTrue();

        svc.apply(ev(StageCommand.DEMOTE, LIVE_ID, ALICE, ALICE, "ev-4", 3));
        assertThat(store.find(LIVE_ID, ALICE).status()).isEqualTo(StageMemberStatus.DEMOTING);
        verify(provider, times(1)).revokePublishPermission(any(StageMember.class));

        StageMember back = svc.confirmLeft(LIVE_ID, ALICE, "ev-5", 4);
        assertThat(back.status()).isEqualTo(StageMemberStatus.AUDIENCE);
        assertThat(back.mayPublish()).isFalse();
    }

    @Test
    void revokeGoesToRevokingAndOnlyConvergesAfterProviderConfirm() {
        StageMember current = promote(ALICE);
        StageMember revoking = svc.apply(ev(StageCommand.REVOKE, LIVE_ID, ALICE, HOST, "ev-r", current.generation()));
        assertThat(revoking.status()).isEqualTo(StageMemberStatus.REVOKING);
        assertThat(revoking.mayPublish()).isFalse();

        assertThatThrownBy(() -> svc.apply(ev(StageCommand.REVOKE, LIVE_ID, ALICE, HOST, "ev-r2", revoking.generation())))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));

        StageMember revoked = svc.confirmRevoked(LIVE_ID, ALICE, "ev-c", revoking.generation());
        assertThat(revoked.status()).isEqualTo(StageMemberStatus.REVOKED);
    }

    @Test
    void providerFailureKeepsMemberPendingAndExposesToReconciler() {
        when(provider.revokePublishPermission(any(StageMember.class))).thenReturn(false);
        StageMember current = promote(BOB);
        svc.apply(ev(StageCommand.DEMOTE, LIVE_ID, BOB, BOB, "ev-d", current.generation()));
        StageMember pending = store.find(LIVE_ID, BOB);
        assertThat(pending.status()).isEqualTo(StageMemberStatus.DEMOTING);
        assertThat(pending.providerPending()).isTrue();
        assertThat(svc.pendingProviderSettlement(LIVE_ID)).contains(pending);
        // 未确认收敛前不标记为 AUDIENCE(契约:不能提前标成已撤)
        assertThat(pending.status()).isNotEqualTo(StageMemberStatus.AUDIENCE);
        verify(provider).revokePublishPermission(any(StageMember.class));
    }

    @Test
    void publisherCapIsEnforced() {
        for (long i = 1; i <= 8; i++) {
            promote(i);
        }
        svc.apply(ev(StageCommand.REQUEST, LIVE_ID, 99L, 99L, "ev-99", 0));
        assertThatThrownBy(() -> svc.apply(ev(StageCommand.APPROVE, LIVE_ID, 99L, HOST, "ev-99a", 1)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.STAGE_LIMIT_REACHED));
    }

    @Test
    void egressRequiresEnabledFlagAndOnStageMember() {
        StageMember current = promote(ALICE);
        // 未启用 -> 拒绝
        assertThatThrownBy(() -> svc.requestStageEgress(LIVE_ID, "room-demo", HOST, "ev-e1"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.PROVIDER_ERROR));
        // 启用但无在台成员 -> 拒绝
        props.getEgress().setEnabled(true);
        when(provider.requestStageEgress(any(), any())).thenReturn("EGR-1");
        svc.apply(ev(StageCommand.DEMOTE, LIVE_ID, ALICE, HOST, "ev-d", current.generation()));
        svc.confirmLeft(LIVE_ID, ALICE, "ev-l", current.generation() + 1);
        assertThatThrownBy(() -> svc.requestStageEgress(LIVE_ID, "room-demo", HOST, "ev-e2"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        verify(provider, never()).requestStageEgress(any(), any());
    }

    @Test
    void egressReturnsProviderRefAndWritesRedactedAudit() {
        StageMember promoter = promote(ALICE);
        props.getEgress().setEnabled(true);
        when(provider.requestStageEgress(any(), any())).thenReturn("EGR-42");
        String ref = svc.requestStageEgress(LIVE_ID, "room-demo", HOST, "ev-e1");
        assertThat(ref).isEqualTo("EGR-42");

        List<StageAuditEntry> audit = store.audit(LIVE_ID, 100);
        StageAuditEntry egressAudit = audit.get(audit.size() - 1);
        assertThat(egressAudit.command()).isEqualTo(StageCommand.EGRESS_START);
        assertThat(egressAudit.userIdRedacted()).matches("[0-9a-f]{16}");
        assertThat(egressAudit.userIdRedacted())
                .isEqualTo(StageAuditEntry.redact(ALICE))
                .isNotEqualTo(String.valueOf(ALICE));
        assertThat(promoter.status()).isEqualTo(StageMemberStatus.ON_STAGE);
        verify(provider).requestStageEgress(any(), any());
    }

    /** 快速把成员推到 ON_STAGE(返回转换后成员)。 */
    private StageMember promote(long userId) {
        svc.apply(ev(StageCommand.REQUEST, LIVE_ID, userId, userId, "ev-req-" + userId, 0));
        svc.apply(ev(StageCommand.APPROVE, LIVE_ID, userId, HOST, "ev-app-" + userId, 1));
        return svc.apply(ev(StageCommand.JOINED, LIVE_ID, userId, HOST, "ev-join-" + userId, 2));
    }
}