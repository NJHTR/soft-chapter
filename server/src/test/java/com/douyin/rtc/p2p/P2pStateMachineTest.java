package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P2pStateMachineTest {

    @Test
    void contractLifecycle() {
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.DISABLED, P2pCommand.EVALUATE))
                .isEqualTo(P2pTopologyStatus.ELIGIBLE);
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.ELIGIBLE, P2pCommand.CONSENT))
                .isEqualTo(P2pTopologyStatus.CONSENTED);
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.CONSENTED, P2pCommand.PROBE_START))
                .isEqualTo(P2pTopologyStatus.PROBING);
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.PROBING, P2pCommand.PROBE_PASS))
                .isEqualTo(P2pTopologyStatus.P2P_CONNECTED);
    }

    @Test
    void probeFailFallsBackThenSfuConfirmed() {
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.PROBING, P2pCommand.PROBE_FAIL))
                .isEqualTo(P2pTopologyStatus.FALLING_BACK);
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.FALLING_BACK, P2pCommand.SFU_CONNECTED_ACK))
                .isEqualTo(P2pTopologyStatus.SFU_CONNECTED);
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.FALLING_BACK, P2pCommand.FAIL))
                .isEqualTo(P2pTopologyStatus.FAILED);
    }

    @Test
    void consentRevokedInConsentedReturnsToEligible() {
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.CONSENTED, P2pCommand.REVOKE))
                .isEqualTo(P2pTopologyStatus.ELIGIBLE);
    }

    @Test
    void revokeDuringProbingFallsBack() {
        assertThat(P2pStateMachine.transition(P2pTopologyStatus.PROBING, P2pCommand.REVOKE))
                .isEqualTo(P2pTopologyStatus.FALLING_BACK);
    }

    @Test
    void fallbackAllowedFromEveryControlledState() {
        for (P2pTopologyStatus s : new P2pTopologyStatus[]{P2pTopologyStatus.ELIGIBLE, P2pTopologyStatus.CONSENTED, P2pTopologyStatus.PROBING}) {
            assertThat(P2pStateMachine.canTransition(s, P2pCommand.FALLBACK)).isTrue();
        }
    }

    @Test
    void unlistedTransitionThrows() {
        assertThatThrownBy(() -> P2pStateMachine.transition(P2pTopologyStatus.P2P_CONNECTED, P2pCommand.PROBE_START))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void noSilentTopologySwitchAfterConnected() {
        assertThat(P2pStateMachine.canTransition(P2pTopologyStatus.P2P_CONNECTED, P2pCommand.FALLBACK)).isFalse();
        assertThat(P2pStateMachine.canTransition(P2pTopologyStatus.P2P_CONNECTED, P2pCommand.SFU_CONNECTED_ACK)).isFalse();
        assertThat(P2pStateMachine.isFinal(P2pTopologyStatus.P2P_CONNECTED)).isTrue();
        assertThat(P2pStateMachine.isFinal(P2pTopologyStatus.SFU_CONNECTED)).isTrue();
        assertThat(P2pStateMachine.isFinal(P2pTopologyStatus.FAILED)).isTrue();
    }
}