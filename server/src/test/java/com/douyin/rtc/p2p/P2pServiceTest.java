package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P2pServiceTest {

    private static final long NOW = 1_800_000_000_000L;

    private P2pProperties props;
    private P2pService service;
    private MutableClock clock;

    static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void advance(long ms) {
            this.millis += ms;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    private Map<String, String> eligibleCtx() {
        Map<String, String> ctx = P2pPolicyRule.context(true, "direct", 2, "ACCEPTED",
                false, false, false, false, true);
        return ctx;
    }

    @BeforeEach
    void setUp() {
        props = new P2pProperties();
        props.setEnabled(true);
        props.setProbeBudgetMs(2000);
        props.setConsentTtlSeconds(60);
        props.setSignalingSecret("test-secret-001");
        clock = new MutableClock(NOW);
        service = new P2pService(new InMemoryP2pStateStore(), props, new P2pMetrics(null), clock);
    }

    @Test
    void evaluateMakesEligibleWhenAllConditionsMet() {
        P2pStatusView v = service.evaluate("c1", 1, eligibleCtx(), "e0", 1L);
        assertThat(v.status()).isEqualTo("ELIGIBLE");
        assertThat(v.eligible()).isTrue();
    }

    @Test
    void disabledFlagThrowsFailClosed() {
        props.setEnabled(false);
        assertThatThrownBy(() -> service.evaluate("c1", 1, eligibleCtx(), "e0", 1L))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.P2P_NOT_ELIGIBLE));
    }

    @Test
    void denyReasonsKeepDisabled() {
        Map<String, String> ctx = P2pPolicyRule.context(true, "group", 2, "ACCEPTED", false, false, false, false, true);
        P2pStatusView v = service.evaluate("c1", 1, ctx, "e0", 1L);
        assertThat(v.status()).isEqualTo("DISABLED");
    }

    @Test
    void dualConsentStartsWithProbeCycleToP2PConnected() {
        evaluateAndConsent();
        P2pStatusView v = service.probeStart("c1", 1, 1L, "e3");
        assertThat(v.status()).isEqualTo("PROBING");
        assertThat(v.probeDeadlineEpochMs()).isEqualTo(NOW + 2000);
        P2pStatusView r = service.probeResult("c1", 1, P2pCandidateClassification.ProbeOutcome.DIRECT,
                "host", "host", 12L, 0L, "e4", 1L);
        assertThat(r.status()).isEqualTo("P2P_CONNECTED");
    }

    private void evaluateAndConsent() {
        service.evaluate("c1", 1, eligibleCtx(), "e0", 1L);
        service.consent("c1", 1, 1L, "e1");
        P2pStatusView afterFirst = service.status("c1", 1);
        assertThat(afterFirst.status()).isEqualTo("ELIGIBLE");
        service.consent("c1", 1, 2L, "e2");
        P2pStatusView afterBoth = service.status("c1", 1);
        assertThat(afterBoth.status()).isEqualTo("CONSENTED");
    }

    @Test
    void relaySelectedPairFallsBackWithReason() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        P2pStatusView r = service.probeResult("c1", 1, P2pCandidateClassification.ProbeOutcome.RELAY,
                "host", "relay", 90L, 0L, "e4", 1L);
        assertThat(r.status()).isEqualTo("FALLING_BACK");
        assertThat(r.fallbackReason()).isEqualTo(P2pFallbackReason.RELAY_REQUIRED.name());
        P2pStatusView sfu = service.confirmSfu("c1", 1, "e5");
        assertThat(sfu.status()).isEqualTo("SFU_CONNECTED");
    }

    @Test
    void revokedConsentDuringProbeFallsBackAsPermissionRevoked() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        P2pStatusView v = service.revokeConsent("c1", 1, 2L, "e4");
        assertThat(v.status()).isEqualTo("FALLING_BACK");
        assertThat(v.fallbackReason()).isEqualTo(P2pFallbackReason.PERMISSION_REVOKED.name());
    }

    @Test
    void consentExpiryFallsBackWithStableReason() {
        evaluateAndConsent();
        clock.advance(61_000L);
        P2pStatusView v = service.probeStart("c1", 1, 1L, "e3");
        assertThat(v.status()).isEqualTo("FALLING_BACK");
        assertThat(v.fallbackReason()).isEqualTo(P2pFallbackReason.CONSENT_EXPIRED.name());
    }

    @Test
    void probeBeforeConsentRejected() {
        service.evaluate("c1", 1, eligibleCtx(), "e0", 1L);
        service.consent("c1", 1, 1L, "e1");
        assertThatThrownBy(() -> service.probeStart("c1", 1, 1L, "e3"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void consentReplayDoesNotExtendTtl() {
        evaluateAndConsent();
        clock.advance(30_000L);
        service.consent("c1", 1, 1L, "e1");
        P2pConsentState after = service.consentState("c1");
        P2pConsentState.ConsentEntry entry = after.getConsents().get("1");
        assertThat(entry.getExpiryEpochMs()).isEqualTo(NOW + 60_000L);
    }

    @Test
    void signalRelayRequiresConsentAndMembership() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        P2pSignalingEnvelope env = signedEnvelope("offer", 1L, 1L, 2L, 1L, "sig-1", "v=0 payload placeholder");
        long seq = service.relaySignal("c1", 1, env, 1L, List.of(1L, 2L));
        assertThat(seq).isEqualTo(1L);
        List<P2pSignalingEnvelope> inbox = service.takeInbox("c1", 2L);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getPayload()).isEqualTo("v=0 payload placeholder");
    }

    @Test
    void signalReplayReturnsSameSeqWithoutDuplicateDelivery() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        P2pSignalingEnvelope env = signedEnvelope("offer", 1L, 1L, 2L, 1L, "sig-1", "payload-a");
        assertThat(service.relaySignal("c1", 1, env, 1L, List.of(1L, 2L))).isEqualTo(1L);
        assertThat(service.relaySignal("c1", 1, env, 1L, List.of(1L, 2L))).isEqualTo(1L);
        assertThat(service.takeInbox("c1", 2L)).hasSize(1);
    }

    @Test
    void outOfOrderSeqRejected() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        service.relaySignal("c1", 1, signedEnvelope("offer", 1L, 1L, 2L, 2L, "sig-2", "p2"), 1L, List.of(1L, 2L));
        assertThatThrownBy(() -> service.relaySignal("c1", 1,
                signedEnvelope("answer", 1L, 1L, 2L, 1L, "sig-3", "p1"), 1L, List.of(1L, 2L)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.SEQ_OUT_OF_ORDER));
    }

    @Test
    void unsignedOrForgedEnvelopeRejected() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        P2pSignalingEnvelope env = signedEnvelope("offer", 1L, 1L, 2L, 1L, "sig-1", "v=0 p");
        env.setSignature("deadbeef");
        assertThatThrownBy(() -> service.relaySignal("c1", 1, env, 1L, List.of(1L, 2L)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void oversizedPayloadRejected() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        StringBuilder big = new StringBuilder();
        while (big.length() <= props.getSignalingMaxBytes()) {
            big.append("x");
        }
        assertThatThrownBy(() -> service.relaySignal("c1", 1,
                signedEnvelope("offer", 1L, 1L, 2L, 1L, "sig-1", big.toString()), 1L, List.of(1L, 2L)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void rateLimitExceededRejected() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        for (int i = 1; i <= props.getSignalRatePerSecond(); i++) {
            service.relaySignal("c1", 1, signedEnvelope("ice", 1L, 1L, 2L, i, "sig-" + i, "ice-" + i), 1L, List.of(1L, 2L));
        }
        assertThatThrownBy(() -> service.relaySignal("c1", 1,
                signedEnvelope("ice", 1L, 1L, 2L, 11L, "sig-11", "ice-11"), 1L, List.of(1L, 2L)))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.RATE_LIMITED));
    }

    @Test
    void auditOnlyKeepsMetadataAndDigests() {
        evaluateAndConsent();
        service.probeStart("c1", 1, 1L, "e3");
        service.relaySignal("c1", 1, signedEnvelope("offer", 1L, 1L, 2L, 1L, "sig-1", "top-secret-sdp"), 1L, List.of(1L, 2L));
        boolean anyRawPayload = service.audit("c1").stream()
                .anyMatch(e -> e.detail().contains("top-secret-sdp"));
        assertThat(anyRawPayload).isFalse();
        assertThat(service.audit("c1")).isNotEmpty();
    }

    @Test
    void fallbackFromEligibleThenFailConverges() {
        service.evaluate("c1", 1, eligibleCtx(), "e0", 1L);
        P2pStatusView fb = service.fallback("c1", 1, P2pFallbackReason.FEATURE_DISABLED, "e5", 1L);
        assertThat(fb.status()).isEqualTo("FALLING_BACK");
        assertThat(fb.fallbackReason()).isEqualTo(P2pFallbackReason.FEATURE_DISABLED.name());
        P2pStatusView failed = service.fail("c1", 1, "budget expired", "e6");
        assertThat(failed.status()).isEqualTo("FAILED");
    }

    private P2pSignalingEnvelope signedEnvelope(String kind, long gen, Long from, Long to, long seq, String eventId, String payload) {
        P2pSignalingEnvelope env = new P2pSignalingEnvelope();
        env.setVersion("v1");
        env.setCallId("c1");
        env.setTopologyGeneration(gen);
        env.setFromUserId(from);
        env.setToUserId(to);
        env.setKind(kind);
        env.setSeq(seq);
        env.setEventId(eventId);
        env.setCreatedEpochMs(NOW);
        env.setPayload(payload);
        env.setSignature(service.signalingSecret() != null && !service.signalingSecret().isEmpty()
                ? new P2pSignalValidator(service.signalingSecret(), props.getSignalingMaxBytes(),
                props.getMaxIceCandidates(), props.getMailboxTtlSeconds() * 1000L, props.getSignalRatePerSecond()).sign(env)
                : null);
        return env;
    }
}