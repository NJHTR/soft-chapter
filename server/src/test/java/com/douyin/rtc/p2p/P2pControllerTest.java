package com.douyin.rtc.p2p;

import com.douyin.common.Result;
import com.douyin.rtc.controller.P2pController;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P2pControllerTest {

    private static final long NOW = 1_800_000_000_000L;

    private P2pService service;
    private P2pCallContextPort callContext;
    private JwtUtil jwtUtil;
    private HttpServletRequest req;
    private P2pController controller;
    private P2pProperties props;
    private P2pServiceTest.MutableClock clock;

    @BeforeEach
    void setUp() {
        props = new P2pProperties();
        props.setEnabled(true);
        props.setSignalingSecret("test-secret-001");
        clock = new P2pServiceTest.MutableClock(NOW);
        service = new P2pService(new InMemoryP2pStateStore(), props, new P2pMetrics(null), clock);
        callContext = mock(P2pCallContextPort.class);
        when(callContext.topologyGeneration("c1")).thenReturn(1L);
        when(callContext.eligibilityContext("c1")).thenReturn(P2pPolicyRule.context(
                true, "direct", 2, "ACCEPTED", false, false, false, false, true));
        when(callContext.participantIds("c1")).thenReturn(List.of(1L, 2L));
        jwtUtil = mock(JwtUtil.class);
        req = mock(HttpServletRequest.class);
        controller = new P2pController(service, callContext, jwtUtil);
    }

    private void login(Long userId) {
        when(req.getHeader("Authorization")).thenReturn("Bearer tok-" + userId);
        when(jwtUtil.getUserIdFromToken("tok-" + userId)).thenReturn(userId);
    }

    private void consentBoth() {
        login(1L);
        controller.evaluate("c1", Map.of("event_id", "e0"), req);
        controller.consent("c1", Map.of("event_id", "e1"), req);
        login(2L);
        controller.consent("c1", Map.of("event_id", "e2"), req);
    }

    @Test
    void fullRoundTripThroughController() {
        consentBoth();
        login(1L);
        Result<?> probe = controller.probeStart("c1", Map.of("event_id", "e3"), req);
        assertThat(((P2pStatusView) probe.getData()).status()).isEqualTo("PROBING");
        Result<?> result = controller.probeResult("c1", Map.of(
                "event_id", "e4", "outcome", "DIRECT", "local_candidate_type", "host",
                "remote_candidate_type", "host", "rtt_ms", "11"), req);
        assertThat(((P2pStatusView) result.getData()).status()).isEqualTo("P2P_CONNECTED");
    }

    @Test
    void relayFailureMapsToStableReasonViaController() {
        consentBoth();
        login(1L);
        controller.probeStart("c1", Map.of("event_id", "e3"), req);
        Result<?> result = controller.probeResult("c1", Map.of(
                "event_id", "e4", "outcome", "RELAY", "local_candidate_type", "host",
                "remote_candidate_type", "relay"), req);
        P2pStatusView view = (P2pStatusView) result.getData();
        assertThat(view.status()).isEqualTo("FALLING_BACK");
        assertThat(view.fallbackReason()).isEqualTo("RELAY_REQUIRED");
    }

    @Test
    void anonymousCallerGetsNotAuthorized() {
        login(null);
        when(req.getHeader("Authorization")).thenReturn(null);
        assertThatThrownBy(() -> controller.topology("c1", req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void invalidOutcomeRejected() {
        login(1L);
        controller.evaluate("c1", Map.of("event_id", "e0"), req);
        controller.consent("c1", Map.of("event_id", "e1"), req);
        login(2L);
        controller.consent("c1", Map.of("event_id", "e2"), req);
        login(1L);
        controller.probeStart("c1", Map.of("event_id", "e3"), req);
        assertThatThrownBy(() -> controller.probeResult("c1", Map.of(
                "event_id", "e4", "outcome", "magic-dialup"), req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(e -> assertThat(((CallDomainException) e).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void signalRelayThroughController() {
        consentBoth();
        login(1L);
        controller.probeStart("c1", Map.of("event_id", "e3"), req);
        P2pSignalingEnvelope env = new P2pSignalingEnvelope();
        env.setCallId("c1");
        env.setTopologyGeneration(1);
        env.setFromUserId(1L);
        env.setToUserId(2L);
        env.setKind("offer");
        env.setSeq(1);
        env.setEventId("sig-1");
        env.setCreatedEpochMs(NOW);
        env.setPayload("v=0 offer");
        env.setSignature(new P2pSignalValidator("test-secret-001", props.getSignalingMaxBytes(),
                props.getMaxIceCandidates(), props.getMailboxTtlSeconds() * 1000L,
                props.getSignalRatePerSecond()).sign(env));
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("version", "v1");
        body.put("call_id", "c1");
        body.put("topology_generation", "1");
        body.put("from_user_id", "1");
        body.put("to_user_id", "2");
        body.put("kind", "offer");
        body.put("seq", "1");
        body.put("event_id", "sig-1");
        body.put("created_epoch_ms", String.valueOf(NOW));
        body.put("payload", "v=0 offer");
        body.put("signature", env.getSignature());
        Result<?> relayed = controller.relaySignal("c1", body, req);
        assertThat(((Map<?, ?>) relayed.getData()).get("seq")).isEqualTo(1L);
        login(2L);
        Result<?> inbox = controller.inbox("c1", req);
        assertThat(((List<?>) inbox.getData())).hasSize(1);
        Result<?> audit = controller.audit("c1", req);
        assertThat(((List<?>) audit.getData())).isNotEmpty();
    }
}