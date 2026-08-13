package com.douyin.rtc;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CreateCallCommand;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 幂等契约: client_request_id 重复创建返回原结果;
 * event_id 相同重放返回当前状态且不创建新记录(事件账本不重复)。
 */
class IdempotencyTest {

    private RtcRepoFixture fx;
    private CallService svc;

    @BeforeEach
    void setUp() {
        fx = new RtcRepoFixture();
        svc = CallTestSupport.service(fx);
    }

    @Test
    void createCallReplayReturnsOriginalResult() {
        CallSession first = CallTestSupport.createDirect(svc, fx, "creq-dup-00001", "evt-create-0001");
        CallSession second = CallTestSupport.createDirect(svc, fx, "creq-dup-00001", "evt-create-0002");

        assertThat(second.getCallId()).isEqualTo(first.getCallId());
        assertThat(second).isSameAs(first);
        assertThat(fx.sessionsByCall).hasSize(1);
        assertThat(fx.eventCount(first.getCallId(), "call.request")).isEqualTo(1);
    }

    @Test
    void createCallWithSameClientRequestIdByDifferentInitiatorRejected() {
        CallTestSupport.createDirect(svc, fx, "creq-dup-00001", "evt-create-0001");
        CreateCallCommand hijacked = CallTestSupport.directCommand(CALLEE, INITIATOR, "creq-dup-00001", "evt-create-0002");

        assertThatThrownBy(() -> svc.createCall(hijacked))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.SESSION_ALREADY_EXISTS));
        assertThat(fx.sessionsByCall).hasSize(1);
    }

    @Test
    void createConcurrentSameClientRequestIdReturnsOriginalAfterInsertConflict() {
        CallSession first = CallTestSupport.createDirect(svc, fx, "creq-race-00001", "evt-create-0001");
        // 模拟并发窗口: 预查未命中(返回 null),INSERT 唯一冲突后回查命中原会话
        when(fx.sessions.findByClientRequestId("creq-race-00001"))
                .thenReturn(null)
                .thenReturn(first);

        CallSession second = CallTestSupport.createDirect(svc, fx, "creq-race-00001", "evt-create-0002");

        assertThat(second.getCallId()).isEqualTo(first.getCallId());
        assertThat(second).isSameAs(first);
        assertThat(fx.sessionsByCall).hasSize(1);
        assertThat(fx.eventCount(first.getCallId(), "call.request")).isEqualTo(1);
    }

    @Test
    void createRejectsReservedSystemEventId() {
        fx.setMutualFriend(INITIATOR, CALLEE);
        assertThatThrownBy(() -> svc.createCall(new CreateCallCommand(
                INITIATOR, "direct", CALLEE, null, "audio", "livekit",
                "creq-sys-00001", "sys:forged", "trace")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void createRejectsReservedTtlEventId() {
        fx.setMutualFriend(INITIATOR, CALLEE);
        assertThatThrownBy(() -> svc.createCall(new CreateCallCommand(
                INITIATOR, "direct", CALLEE, null, "audio", "livekit",
                "creq-ttl-00001", "ttl:call-x:123456", "trace")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void commandRejectsReservedEventIdPrefix() {
        CallSession call = CallTestSupport.createDirect(svc, fx);

        assertThatThrownBy(() -> svc.acceptCall(call.getCallId(), CALLEE, "sys:accept", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> svc.hangupCall(call.getCallId(), INITIATOR, "ttl:hangup:1", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.accept")).isEqualTo(0);
        assertThat(fx.eventCount(call.getCallId(), "call.hangup")).isEqualTo(0);
    }

    @Test
    void createReplayAfterProgressReturnsTheSameCall() {
        CallSession created = CallTestSupport.createDirect(svc, fx, "creq-rep-00001", "evt-create-0001");
        svc.acceptCall(created.getCallId(), CALLEE, "evt-accept-0001", "trace");

        CallSession replay = CallTestSupport.createDirect(svc, fx, "creq-rep-00001", "evt-create-0002");

        assertThat(replay.getCallId()).isEqualTo(created.getCallId());
        assertThat(replay.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(fx.sessionsByCall).hasSize(1);
    }

    @Test
    void acceptWithSameEventIdReplayedIdempotently() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession first = svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        CallSession replay = svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");

        assertThat(first.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(replay.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.accept")).isEqualTo(1);
    }

    @Test
    void rejectWithSameEventIdReplayedIdempotently() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession first = svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");
        CallSession replay = svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");

        assertThat(first.getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(replay.getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.reject")).isEqualTo(1);
    }

    @Test
    void cancelWithSameEventIdReplayedIdempotently() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        CallSession first = svc.cancelCall(call.getCallId(), INITIATOR, "evt-cancel-0001", "trace");
        CallSession replay = svc.cancelCall(call.getCallId(), INITIATOR, "evt-cancel-0001", "trace");

        assertThat(first.getState()).isEqualTo(CallState.CANCELLED.name());
        assertThat(replay.getState()).isEqualTo(CallState.CANCELLED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.cancel")).isEqualTo(1);
    }

    @Test
    void hangupWithSameEventIdReplayedIdempotently() {
        CallSession call = CallTestSupport.makeConnected(svc, fx);
        CallSession first = svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");
        CallSession replay = svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");

        assertThat(first.getState()).isEqualTo(CallState.ENDING.name());
        assertThat(replay.getState()).isEqualTo(CallState.ENDING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.hangup")).isEqualTo(1);
    }

    @Test
    void ledgerIgnoresDuplicateEventIdInsert() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        com.douyin.rtc.domain.CallEvent appended =
                new com.douyin.rtc.service.CallLedgerService(fx.events, fx.projection)
                        .append("evt-create-0001", call.getCallId(), INITIATOR,
                                com.douyin.rtc.domain.CallEventKind.CALL_REQUEST, null, "trace");
        assertThat(appended).isNull();
        assertThat(fx.eventCount(call.getCallId(), "call.request")).isEqualTo(1);
    }
}