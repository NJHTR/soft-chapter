package com.douyin.rtc;

import com.douyin.common.Result;
import com.douyin.rtc.controller.RtcCallController;
import com.douyin.rtc.controller.RtcExceptionHandler;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.provider.LiveKitTokenService;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.webhook.LiveKitWebhookService;
import com.douyin.rtc.webhook.WebhookSignatureVerifier;
import com.douyin.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static com.douyin.rtc.support.CallTestSupport.STRANGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REST 入口契约: 通话详情强制登录 + 参与者鉴权;
 * token/call 入口数字参数解析失败映射 INVALID_ARGUMENT(不 500)。
 * 纯 JUnit5 + Mockito,不启动 Spring。
 */
class RtcCallControllerTest {

    private JwtUtil jwtUtil;
    private CallService callService;
    private LiveKitTokenService tokenService;
    private LiveKitWebhookService webhookService;
    private WebhookSignatureVerifier signatureVerifier;
    private ObjectMapper objectMapper;
    private HttpServletRequest req;
    private RtcCallController controller;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        callService = mock(CallService.class);
        tokenService = mock(LiveKitTokenService.class);
        webhookService = mock(LiveKitWebhookService.class);
        signatureVerifier = mock(WebhookSignatureVerifier.class);
        objectMapper = new ObjectMapper();
        req = mock(HttpServletRequest.class);
        controller = new RtcCallController(jwtUtil, callService, tokenService,
                webhookService, signatureVerifier, objectMapper);
    }

    private void login(Long userId) {
        when(req.getHeader("Authorization")).thenReturn("Bearer tok-" + userId);
        when(jwtUtil.getUserIdFromToken("tok-" + userId)).thenReturn(userId);
    }

    private CallSession session(String callId) {
        CallSession s = new CallSession();
        s.setCallId(callId);
        s.setState(CallState.RINGING.name());
        s.setInitiatorId(INITIATOR);
        return s;
    }

    // ==================== #1 详情鉴权 ====================

    @Test
    void detailRequiresLogin() {
        when(req.getHeader("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> controller.detail("call-1", req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void detailRejectsNonParticipant() {
        login(STRANGER);
        CallSession session = session("call-1");
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", STRANGER)).thenReturn(null);

        assertThatThrownBy(() -> controller.detail("call-1", req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void detailUnknownCallReturnsNotFound() {
        login(INITIATOR);
        when(callService.getCall("call-404")).thenReturn(null);

        assertThatThrownBy(() -> controller.detail("call-404", req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.SESSION_NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    @Test
    void detailAllowsParticipant() {
        login(INITIATOR);
        CallSession session = session("call-1");
        when(callService.getCall("call-1")).thenReturn(session);
        CallParticipant p = new CallParticipant();
        p.setCallId("call-1");
        p.setUserId(INITIATOR);
        p.setRole("initiator");
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(p);
        when(callService.participants("call-1")).thenReturn(List.of(p));
        when(callService.events("call-1")).thenReturn(List.of());

        Result<?> result = controller.detail("call-1", req);

        assertThat(result.getCode()).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data).containsKeys("call", "participants", "events");
        assertThat(((CallSession) data.get("call")).getCallId()).isEqualTo("call-1");
    }

    // ==================== #7 数字参数解析 ====================

    @Test
    void tokenRejectsNonNumericTtlAsInvalidArgument() {
        login(INITIATOR);
        Map<String, Object> body = Map.of("call_id", "call-1", "ttl_seconds", "abc");

        assertThatThrownBy(() -> controller.token(body, req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void createRejectsNonNumericTargetUserIdAsInvalidArgument() {
        login(INITIATOR);
        Map<String, Object> body = Map.of(
                "scope", "direct", "target_user_id", "not-a-number",
                "client_request_id", "creq-badnum-0001");

        assertThatThrownBy(() -> controller.create(body, req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void numberFormatMappedToInvalidArgumentByAdvice() {
        Result<?> result = new RtcExceptionHandler()
                .handleNumberFormat(new NumberFormatException("abc"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).startsWith(CallErrorCode.INVALID_ARGUMENT.name());
    }
}