package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.douyin.rtc.p2p.P2pCandidateClassification.ProbeOutcome;

class P2pCandidateClassificationTest {

    @Test
    void hostHostIsDirect() {
        assertThat(P2pCandidateClassification.deriveProbeOutcome("host", "host")).isEqualTo(ProbeOutcome.DIRECT);
    }

    @Test
    void anySrflxOrPrflxIsSrflx() {
        assertThat(P2pCandidateClassification.deriveProbeOutcome("host", "srflx")).isEqualTo(ProbeOutcome.SRFLX);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("srflx", "host")).isEqualTo(ProbeOutcome.SRFLX);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("prflx", "host")).isEqualTo(ProbeOutcome.SRFLX);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("srflx", "prflx")).isEqualTo(ProbeOutcome.SRFLX);
    }

    @Test
    void anyRelayIsRelay() {
        assertThat(P2pCandidateClassification.deriveProbeOutcome("host", "relay")).isEqualTo(ProbeOutcome.RELAY);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("relay", "srflx")).isEqualTo(ProbeOutcome.RELAY);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("relay", "relay")).isEqualTo(ProbeOutcome.RELAY);
    }

    @Test
    void unknownCandidateFailsClosedAsRelay() {
        assertThat(P2pCandidateClassification.deriveProbeOutcome(null, "host")).isEqualTo(ProbeOutcome.RELAY);
        assertThat(P2pCandidateClassification.deriveProbeOutcome("webrtc", "host")).isEqualTo(ProbeOutcome.RELAY);
    }

    @Test
    void relayMapsToStableFallbackReason() {
        assertThat(P2pCandidateClassification.fallbackReasonFor(ProbeOutcome.RELAY))
                .isEqualTo(P2pFallbackReason.RELAY_REQUIRED);
        assertThat(P2pCandidateClassification.fallbackReasonFor(ProbeOutcome.SFU))
                .isEqualTo(P2pFallbackReason.TURN_ONLY);
        assertThatThrownBy(() -> P2pCandidateClassification.fallbackReasonFor(ProbeOutcome.DIRECT))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode()).isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }
}