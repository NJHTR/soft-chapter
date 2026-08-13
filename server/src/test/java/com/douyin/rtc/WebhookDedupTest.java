package com.douyin.rtc;

import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.webhook.LiveKitWebhookService;
import com.douyin.rtc.webhook.RtcWebhookLedger;
import com.douyin.rtc.webhook.RtcWebhookLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * webhook 去重契约: 同一 event id 两次落账本只记一次(INSERT IGNORE 语义),
 * 重放返回幂等 duplicate=true 且不重复执行状态动作(DoD: event id 去重重放)。
 * 纯 JUnit5 + Mockito。
 */
class WebhookDedupTest {

    private static final Long INITIATOR = 1001L;
    private static final Long MEMBER = 1002L;

    private RtcWebhookLedgerMapper ledgerMapper;
    private CallService callService;
    private LiveKitWebhookService service;
    private final Set<String> ledger = new HashSet<>();

    @BeforeEach
    void setUp() {
        ledgerMapper = mock(RtcWebhookLedgerMapper.class);
        callService = mock(CallService.class);
        when(ledgerMapper.insertIgnore(any(RtcWebhookLedger.class))).thenAnswer(inv -> {
            RtcWebhookLedger l = inv.getArgument(0);
            return ledger.add(l.getEventId()) ? 1 : 0;
        });
        service = new LiveKitWebhookService(ledgerMapper, callService);

        CallSession session = new CallSession();
        session.setCallId("call-1");
        session.setState(CallState.NEGOTIATING.name());
        session.setInitiatorId(INITIATOR);
        when(callService.getCall("call-1")).thenReturn(session);
        when(callService.participants("call-1")).thenReturn(java.util.List.of(participant(INITIATOR, "CONNECTED")));
    }

    @Test
    void sameEventIdProcessedOnlyOnce() {
        Map<String, Object> payload = Map.of(
                "event", "participant_joined",
                "id", "evt-dup-0001",
                "room", Map.of("name", "call-1"),
                "participant", Map.of("identity", String.valueOf(MEMBER)));

        LiveKitWebhookService.WebhookHandleResult first =
                service.handle("evt-dup-0001", "participant_joined", "call-1", payload, "{}");
        LiveKitWebhookService.WebhookHandleResult second =
                service.handle("evt-dup-0001", "participant_joined", "call-1", payload, "{}");

        assertThat(first.handled()).isTrue();
        assertThat(first.duplicate()).isFalse();
        assertThat(second.handled()).isTrue();
        assertThat(second.duplicate()).isTrue();
        assertThat(ledger).hasSize(1);
        verify(callService, times(1)).joinCall("call-1", MEMBER, "evt-dup-0001", "evt-dup-0001");

        // INSERT IGNORE 执行了两次,但只有一次真正落账本(0 行 = 重放被忽略)
        ArgumentCaptor<RtcWebhookLedger> captor = ArgumentCaptor.forClass(RtcWebhookLedger.class);
        verify(ledgerMapper, times(2)).insertIgnore(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        RtcWebhookLedger recorded = captor.getAllValues().get(0);
        assertThat(recorded.getEventId()).isEqualTo("evt-dup-0001");
        assertThat(recorded.getCallId()).isEqualTo("call-1");
        assertThat(recorded.getEventType()).isEqualTo("participant_joined");
        assertThat(recorded.getProcessed()).isEqualTo(1);
    }

    @Test
    void differentEventIdsBothProcessed() {
        Map<String, Object> joined = Map.of(
                "event", "participant_joined",
                "id", "evt-a1",
                "room", Map.of("name", "call-1"),
                "participant", Map.of("identity", String.valueOf(MEMBER)));
        Map<String, Object> left = Map.of(
                "event", "participant_left",
                "id", "evt-b2",
                "room", Map.of("name", "call-1"),
                "participant", Map.of("identity", String.valueOf(MEMBER)));

        service.handle("evt-a1", "participant_joined", "call-1", joined, "{}");
        service.handle("evt-b2", "participant_left", "call-1", left, "{}");

        assertThat(ledger).hasSize(2);
        verify(callService).joinCall("call-1", MEMBER, "evt-a1", "evt-a1");
        verify(callService).leaveCall("call-1", MEMBER, "evt-b2", "evt-b2");
    }

    @Test
    void roomFinishedConvergesConnectedToEndedWithDerivedEventId() {
        CallSession connected = new CallSession();
        connected.setCallId("call-1");
        connected.setState(CallState.CONNECTED.name());
        connected.setInitiatorId(INITIATOR);
        when(callService.getCall("call-1")).thenReturn(connected);

        Map<String, Object> payload = Map.of(
                "event", "room_finished", "id", "evt-fin-0001", "room", Map.of("name", "call-1"));

        LiveKitWebhookService.WebhookHandleResult r =
                service.handle("evt-fin-0001", "room_finished", "call-1", payload, "{}");

        assertThat(r.handled()).isTrue();
        assertThat(r.duplicate()).isFalse();
        // CONNECTED → hangup(宪法 §2 CONNECTED|hangup|ENDING)→ confirmEnded(ENDING|ended|ENDED)
        verify(callService).hangupCall("call-1", INITIATOR, "evt-fin-0001", "evt-fin-0001");
        verify(callService).confirmEnded("call-1", "evt-fin-0001:converge", "evt-fin-0001");
    }

    @Test
    void unknownRoomIsAuditedWithoutStateAction() {
        when(callService.getCall("call-ghost")).thenReturn(null);
        Map<String, Object> payload = Map.of("event", "room_finished", "id", "evt-ghost", "room", Map.of("name", "call-ghost"));

        LiveKitWebhookService.WebhookHandleResult r =
                service.handle("evt-ghost", "room_finished", "call-ghost", payload, "{}");

        assertThat(r.handled()).isTrue();
        assertThat(ledger).contains("evt-ghost");
        verify(callService, times(0)).confirmEnded(any(), any(), any());
    }

    @Test
    void missingEventIdRejectedWithoutLedgerWrite() {
        LiveKitWebhookService.WebhookHandleResult r =
                service.handle(null, "room_finished", "call-1", Map.of(), "{}");

        assertThat(r.handled()).isFalse();
        assertThat(ledger).isEmpty();
    }

    private static CallParticipant participant(Long userId, String state) {
        CallParticipant p = new CallParticipant();
        p.setCallId("call-1");
        p.setUserId(userId);
        p.setRole(INITIATOR.equals(userId) ? "initiator" : "member");
        p.setState(state);
        return p;
    }
}