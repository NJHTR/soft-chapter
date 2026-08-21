package com.douyin.rtc;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.service.CallReconciliationService;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallReconciliationServiceTest {

    private RtcRepoFixture fixture;
    private SessionManager sessions;
    private CallReconciliationService reconciliation;

    @BeforeEach
    void setUp() {
        fixture = new RtcRepoFixture();
        sessions = mock(SessionManager.class);
        reconciliation = new CallReconciliationService(
                fixture.sessions, fixture.participants, sessions,
                new ObjectMapper().findAndRegisterModules());
    }

    @SuppressWarnings("unchecked")
    @Test
    void offlineCalleeLoginFindsDurableRingingCall() {
        CallService service = CallTestSupport.service(fixture);
        CallSession call = CallTestSupport.createDirect(service, fixture);

        Map<String, Object> result = reconciliation.activeCalls(CALLEE);

        assertThat(result).containsKey("server_now");
        assertThat((List<CallSession>) result.get("calls"))
                .extracting(CallSession::getCallId)
                .containsExactly(call.getCallId());
    }

    @Test
    void repeatedReconnectSendsSameVersionWithoutCreatingDuplicateCall() {
        CallService service = CallTestSupport.service(fixture);
        CallSession call = CallTestSupport.createDirect(service, fixture);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("device-web");

        reconciliation.reconcileSession(CALLEE, socket);
        reconciliation.reconcileSession(CALLEE, socket);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sessions, times(2)).push(eq(socket), payload.capture());
        assertThat(payload.getAllValues()).allSatisfy(json -> {
            assertThat(json).contains("rtc.call.reconciliation");
            assertThat(json).contains(call.getCallId());
            assertThat(json).contains("state_version");
        });
        assertThat(fixture.sessionsByCall).hasSize(1);
    }

    @Test
    void committedStateBroadcastTargetsAllParticipantDevices() {
        CallService service = CallTestSupport.service(fixture);
        CallSession call = CallTestSupport.createDirect(service, fixture);

        reconciliation.broadcastCall(call.getCallId(), "evt-state-1");

        verify(sessions).push(eq(INITIATOR), org.mockito.ArgumentMatchers.contains("rtc.call.state"));
        verify(sessions).push(eq(CALLEE), org.mockito.ArgumentMatchers.contains("rtc.call.state"));
    }
}
