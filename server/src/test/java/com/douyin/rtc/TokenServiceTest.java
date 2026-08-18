package com.douyin.rtc;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.provider.LiveKitTokenService;
import com.douyin.rtc.provider.RtcProperties;
import com.douyin.rtc.provider.TokenResult;
import com.douyin.rtc.service.CallService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LiveKit token 签发契约: 权限矩阵(发起者/成员/非成员/过期/终态/未接听)、
 * TTL 边界(59/60/900/901)、claims 结构(宪法 §2.4/§2.7 服务端决定权限)。
 * 纯 JUnit5 + Mockito,不启动 Spring。
 */
class TokenServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "unit-test-secret-0123456789abcdef0123";
    private static final Long INITIATOR = 1001L;
    private static final Long MEMBER = 1002L;
    private static final Long STRANGER = 9999L;

    private CallService callService;
    private RtcProperties props;
    private LiveKitTokenService service;

    @BeforeEach
    void setUp() {
        callService = mock(CallService.class);
        props = new RtcProperties();
        props.setTokenTtlSeconds(300);
        props.setLivekitApiKey(API_KEY);
        props.setLivekitApiSecret(API_SECRET);
        service = new LiveKitTokenService(props, callService);
    }

    // ==================== 权限矩阵 ====================

    @Test
    void initiatorInAcceptedGetsTokenAndAdvancesNegotiation() {
        CallSession session = session("call-1", CallState.ACCEPTED, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        TokenResult r = service.issue("call-1", INITIATOR, null, "trace");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(callService).startNegotiation(eq("call-1"), eq(INITIATOR), captor.capture(), eq("trace"));
        assertThat(captor.getValue()).startsWith("sys:token:");

        Claims claims = parse(r.token());
        assertThat(claims.getSubject()).isEqualTo("1001");
        @SuppressWarnings("unchecked")
        Map<String, Object> video = claims.get("video", Map.class);
        assertThat(video).containsEntry("room", "call-1");
        assertThat(video).containsEntry("roomJoin", true);
        assertThat(video).containsEntry("canPublish", true);
        assertThat(video).containsEntry("canSubscribe", true);
        assertThat(claims.getIssuer()).isEqualTo(API_KEY);
        assertThat(claims.getExpiration()).isEqualTo(new Date(claims.getIssuedAt().getTime() + 300_000));
    }

    @Test
    void tokenUsesHs256AcceptedByLiveKit() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR))
                .thenReturn(participant("call-1", INITIATOR, "initiator"));

        TokenResult result = service.issue("call-1", INITIATOR, null, "trace");
        String header = new String(
                Base64.getUrlDecoder().decode(result.token().substring(0, result.token().indexOf('.'))),
                StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void acceptedMemberCanPublishAndNegotiationAdvance() {
        CallSession session = session("call-1", CallState.ACCEPTED, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", MEMBER)).thenReturn(participant("call-1", MEMBER, "member"));

        TokenResult r = service.issue("call-1", MEMBER, null, "trace");

        verify(callService).startNegotiation(eq("call-1"), eq(MEMBER), anyString(), eq("trace"));
        Claims claims = parse(r.token());
        assertThat(videoClaims(claims)).containsEntry("canPublish", true);
    }

    @Test
    void groupMemberMustAcceptBeforeTokenIssuance() {
        CallSession session = session("call-group-1", CallState.ACCEPTED, null);
        session.setScope("group");
        when(callService.getCall("call-group-1")).thenReturn(session);
        CallParticipant member = participant("call-group-1", MEMBER, "member");
        member.setState("RINGING");
        when(callService.getParticipant("call-group-1", MEMBER)).thenReturn(member);

        assertThatThrownBy(() -> service.issue("call-group-1", MEMBER, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
        verify(callService, never()).startNegotiation(anyString(), any(), anyString(), anyString());
    }

    @Test
    void acceptedGroupMemberCanObtainToken() {
        CallSession session = session("call-group-2", CallState.ACCEPTED, null);
        session.setScope("group");
        when(callService.getCall("call-group-2")).thenReturn(session);
        CallParticipant member = participant("call-group-2", MEMBER, "member");
        member.setState("JOINING");
        when(callService.getParticipant("call-group-2", MEMBER)).thenReturn(member);

        TokenResult result = service.issue("call-group-2", MEMBER, null, "trace");

        assertThat(result.token()).isNotBlank();
        verify(callService).startNegotiation(eq("call-group-2"), eq(MEMBER), anyString(), eq("trace"));
    }

    @Test
    void reissueInNegotiatingSkipsNegotiationStart() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        service.issue("call-1", INITIATOR, null, "trace");

        verify(callService, never()).startNegotiation(anyString(), any(), anyString(), anyString());
    }

    @Test
    void tokenInConnectedAllowsReconnect() {
        CallSession session = session("call-1", CallState.CONNECTED, null);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", MEMBER)).thenReturn(participant("call-1", MEMBER, "member"));

        TokenResult r = service.issue("call-1", MEMBER, null, "trace");

        assertThat(videoClaims(parse(r.token()))).containsEntry("canPublish", true);
    }

    @Test
    void nonMemberRejected() {
        CallSession session = session("call-1", CallState.ACCEPTED, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", STRANGER)).thenReturn(null);

        assertThatThrownBy(() -> service.issue("call-1", STRANGER, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void ringingNotYetAcceptedRejected() {
        CallSession session = session("call-1", CallState.RINGING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void terminalCallRejected() {
        CallSession session = session("call-1", CallState.ENDED, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void expiredNegotiatingWindowRejected() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.CALL_EXPIRED));
    }

    @Test
    void unknownCallRejected() {
        when(callService.getCall("call-404")).thenReturn(null);

        assertThatThrownBy(() -> service.issue("call-404", INITIATOR, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void anonymousActorRejected() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);

        assertThatThrownBy(() -> service.issue("call-1", null, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void missingApiSecretFailsClosed() {
        props.setLivekitApiKey(null);
        props.setLivekitApiSecret(null);
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, null, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.PROVIDER_ERROR));
    }

    // ==================== TTL 边界 (60-900s) ====================

    @Test
    void ttlBoundaries() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, 59, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.INVALID_ARGUMENT));

        assertThat(ttlOf(service.issue("call-1", INITIATOR, 60, "trace"))).isEqualTo(60);
        assertThat(ttlOf(service.issue("call-1", INITIATOR, 900, "trace"))).isEqualTo(900);

        assertThatThrownBy(() -> service.issue("call-1", INITIATOR, 901, "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode()).isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void defaultTtlFromConfigWhenNotRequested() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        TokenResult r = service.issue("call-1", INITIATOR, null, "trace");

        assertThat(r.ttlSeconds()).isEqualTo(300);
        assertThat(ttlOf(r)).isEqualTo(300);
        assertThat(r.expiresAt() - System.currentTimeMillis() / 1000).isBetween(299L, 300L);
    }

    // ==================== 房间名与响应 ====================

    @Test
    void roomNameDerivedFromCallIdWhenRoomIdEmpty() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        TokenResult r = service.issue("call-1", INITIATOR, null, "trace");

        assertThat(r.roomName()).isEqualTo("call-1");
        assertThat(videoClaims(parse(r.token()))).containsEntry("room", "call-1");
    }

    @Test
    void roomIdUsedWhenPresent() {
        CallSession session = session("call-1", CallState.NEGOTIATING, null);
        session.setRoomId("room-abc");
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.getParticipant("call-1", INITIATOR)).thenReturn(participant("call-1", INITIATOR, "initiator"));

        TokenResult r = service.issue("call-1", INITIATOR, null, "trace");

        assertThat(r.roomName()).isEqualTo("room-abc");
        assertThat(videoClaims(parse(r.token()))).containsEntry("room", "room-abc");
    }

    // ==================== 工具 ====================

    private CallSession session(String callId, CallState state, String roomId) {
        CallSession s = new CallSession();
        s.setCallId(callId);
        s.setState(state.name());
        s.setRoomId(roomId);
        s.setInitiatorId(INITIATOR);
        return s;
    }

    private CallParticipant participant(String callId, Long userId, String role) {
        CallParticipant p = new CallParticipant();
        p.setCallId(callId);
        p.setUserId(userId);
        p.setRole(role);
        return p;
    }

    private long ttlOf(TokenResult r) {
        Claims claims = parse(r.token());
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
    }

    private Map<String, Object> videoClaims(Claims claims) {
        Object video = claims.get("video");
        return video instanceof Map<?, ?> m ? cast(m) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
