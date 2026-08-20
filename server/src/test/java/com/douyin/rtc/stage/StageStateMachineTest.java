package com.douyin.rtc.stage;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 契约 §6 状态机转移表(含撤销分叉与内部收敛)。
 */
class StageStateMachineTest {

    @Test
    void audienceCanRequestAndCancel() {
        assertThat(StageStateMachine.transition(StageMemberStatus.AUDIENCE, StageCommand.REQUEST))
                .isEqualTo(StageMemberStatus.REQUESTED);
        assertThat(StageStateMachine.transition(StageMemberStatus.REQUESTED, StageCommand.LEFT))
                .isEqualTo(StageMemberStatus.AUDIENCE);
    }

    @Test
    void approvePromotesAndJoinedOnStage() {
        assertThat(StageStateMachine.transition(StageMemberStatus.REQUESTED, StageCommand.APPROVE))
                .isEqualTo(StageMemberStatus.PROMOTING);
        assertThat(StageStateMachine.transition(StageMemberStatus.PROMOTING, StageCommand.JOINED))
                .isEqualTo(StageMemberStatus.ON_STAGE);
    }

    @Test
    void promotedButNeverJoinedCanBeCancelled() {
        assertThat(StageStateMachine.transition(StageMemberStatus.PROMOTING, StageCommand.LEFT))
                .isEqualTo(StageMemberStatus.AUDIENCE);
    }

    @Test
    void demoteConvergesToAudienceViaProviderLeft() {
        assertThat(StageStateMachine.transition(StageMemberStatus.ON_STAGE, StageCommand.DEMOTE))
                .isEqualTo(StageMemberStatus.DEMOTING);
        assertThat(StageStateMachine.transition(StageMemberStatus.DEMOTING, StageCommand.LEFT))
                .isEqualTo(StageMemberStatus.AUDIENCE);
        assertThat(StageStateMachine.transition(StageMemberStatus.DEMOTING, StageCommand.CONFIRM_LEFT))
                .isEqualTo(StageMemberStatus.AUDIENCE);
    }

    @Test
    void revokeFromAnyControlledStateLandsInRevoking() {
        for (StageMemberStatus from : new StageMemberStatus[]{
                StageMemberStatus.REQUESTED, StageMemberStatus.PROMOTING,
                StageMemberStatus.ON_STAGE, StageMemberStatus.DEMOTING}) {
            assertThat(StageStateMachine.transition(from, StageCommand.REVOKE))
                    .as("revoke from %s", from)
                    .isEqualTo(StageMemberStatus.REVOKING);
        }
        assertThat(StageStateMachine.transition(StageMemberStatus.REVOKING, StageCommand.CONFIRM_REVOKED))
                .isEqualTo(StageMemberStatus.REVOKED);
    }

    @Test
    void revokingDoesNotAcceptDemoteOrLeft() {
        assertThatThrownBy(() -> StageStateMachine.transition(StageMemberStatus.REVOKING, StageCommand.DEMOTE))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> StageStateMachine.transition(StageMemberStatus.REVOKED, StageCommand.REQUEST))
                .isInstanceOf(CallDomainException.class);
    }

    @Test
    void revokedIsFinal() {
        assertThat(StageStateMachine.isFinal(StageMemberStatus.REVOKED)).isTrue();
        assertThat(StageStateMachine.isFinal(StageMemberStatus.ON_STAGE)).isFalse();
    }

    @Test
    void slotAndSettlementHelpers() {
        assertThat(StageMemberStatus.PROMOTING.holdsPublishSlot()).isTrue();
        assertThat(StageMemberStatus.ON_STAGE.holdsPublishSlot()).isTrue();
        assertThat(StageMemberStatus.REQUESTED.holdsPublishSlot()).isFalse();
        assertThat(StageMemberStatus.DEMOTING.providerSettlementPending()).isTrue();
        assertThat(StageMemberStatus.REVOKING.providerSettlementPending()).isTrue();
        assertThat(StageMemberStatus.AUDIENCE.providerSettlementPending()).isFalse();
    }
}