package com.douyin.rtc;

import com.douyin.rtc.domain.CallEndReason;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 乱序/过期契约: 乱序命令安全返回当前状态且不创建新记录;
 * 过期命令抛 CALL_EXPIRED;终态后新命令返回当前状态;seq 单调递增。
 */
class OutOfOrderTest {

    private RtcRepoFixture fx;
    private CallService svc;

    @BeforeEach
    void setUp() {
        fx = new RtcRepoFixture();
        svc = CallTestSupport.service(fx);
    }

    @Test
    void acceptAfterAcceptedReturnsCurrentStateWithoutNewRecord() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        CallSession again = svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0002", "trace");

        assertThat(again.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.accept")).isEqualTo(1);
    }

    @Test
    void rejectAfterAcceptedReturnsCurrentStateWithoutNewRecord() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        CallSession lateReject = svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");

        assertThat(lateReject.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.reject")).isEqualTo(0);
    }

    @Test
    void hangupWhileRingingReturnsCurrentState() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession result = svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");

        assertThat(result.getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.hangup")).isEqualTo(0);
    }

    @Test
    void newCommandAfterTerminalReturnsCurrentState() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");
        CallSession hangupAfterReject = svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");

        assertThat(hangupAfterReject.getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.hangup")).isEqualTo(0);
        assertThat(fx.eventsOf(call.getCallId())).hasSize(2); // call.request + call.reject
    }

    @Test
    void acceptOnExpiredSessionThrowsCallExpired() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.CALL_EXPIRED));
    }

    @Test
    void expireWorkerMovesRingingToExpired() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        CallSession after = svc.expireCall(call.getCallId(), "ttl:expire", "ttl-worker");

        assertThat(after.getState()).isEqualTo(CallState.EXPIRED.name());
        assertThat(after.getEndReason()).isEqualTo(CallEndReason.EXPIRED.name());
        assertThat(after.getEndedAt()).isNotNull();
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(1);
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("CANCELLED");
    }

    @Test
    void expireWorkerMovesNegotiatingToFailedWithExpiredReason() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        svc.startNegotiation(call.getCallId(), CALLEE, "evt-neg-0001", "trace");
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        CallSession after = svc.expireCall(call.getCallId(), "ttl:expire", "ttl-worker");

        assertThat(after.getState()).isEqualTo(CallState.FAILED.name());
        assertThat(after.getEndReason()).isEqualTo(CallEndReason.EXPIRED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.failed")).isEqualTo(1);
    }

    @Test
    void expireWorkerIgnoresTerminalSession() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        CallSession after = svc.expireCall(call.getCallId(), "ttl:expire", "ttl-worker");

        assertThat(after.getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(0);
    }

    @Test
    void expireIgnoresNotYetExpiredSession() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession after = svc.expireCall(call.getCallId(), "ttl:expire", "ttl-worker");

        assertThat(after.getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(0);
    }

    @Test
    void joinBeforeAcceptReturnsCurrentState() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession after = svc.joinCall(call.getCallId(), INITIATOR, "evt-join-0001", "trace");

        assertThat(after.getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("RINGING");
        assertThat(fx.eventCount(call.getCallId(), "call.join")).isEqualTo(0);
    }

    @Test
    void seqIsMonotonicPerCallAndParticipant() {
        CallSession call = CallTestSupport.makeConnected(svc, fx);
        svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");
        svc.confirmEnded(call.getCallId(), "evt-ended-0001", "trace");

        List<CallEvent> events = fx.eventsOf(call.getCallId());
        assertThat(events).isNotEmpty();
        Map<String, List<Long>> seqs = events.stream().collect(Collectors.groupingBy(
                e -> e.getCallId() + "|" + e.getParticipantId(),
                Collectors.mapping(CallEvent::getSeq, Collectors.toList())));
        for (List<Long> seqList : seqs.values()) {
            long prev = 0;
            for (long seq : seqList) {
                assertThat(seq).isGreaterThan(prev);
                prev = seq;
            }
        }
        // 所有事件都带正 seq
        assertThat(events).allSatisfy(e -> assertThat(e.getSeq()).isPositive());
    }

    @Test
    void confirmEndedBeforeEndingReturnsCurrentState() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession result = svc.confirmEnded(call.getCallId(), "evt-ended-0001", "trace");

        assertThat(result.getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.ended")).isEqualTo(0);
    }
}