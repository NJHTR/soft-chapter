package com.douyin.rtc;

import com.douyin.rtc.domain.CallEndReason;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CallTtlWorker;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TTL worker 契约: 每轮扫描只回收已过期的 RINGING/NEGOTIATING,
 * 只处理终态前(守卫式,已被推进的状态跳过)。
 */
class CallTtlWorkerTest {

    private RtcRepoFixture fx;
    private CallService svc;
    private CallTtlWorker worker;

    @BeforeEach
    void setUp() {
        fx = new RtcRepoFixture();
        svc = CallTestSupport.service(fx);
        worker = new CallTtlWorker(svc, fx.sessions);
    }

    @Test
    void sweepExpiresRingingSession() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        worker.sweep();

        CallSession after = fx.session(call.getCallId());
        assertThat(after.getState()).isEqualTo(CallState.EXPIRED.name());
        assertThat(after.getEndReason()).isEqualTo(CallEndReason.EXPIRED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(1);
    }

    @Test
    void sweepFailsExpiredNegotiatingSession() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        svc.startNegotiation(call.getCallId(), CALLEE, "evt-neg-0001", "trace");
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        worker.sweep();

        CallSession after = fx.session(call.getCallId());
        assertThat(after.getState()).isEqualTo(CallState.FAILED.name());
        assertThat(after.getEndReason()).isEqualTo(CallEndReason.EXPIRED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.failed")).isEqualTo(1);
    }

    @Test
    void sweepSkipsNotYetExpiredSessions() {
        CallSession call = CallTestSupport.createDirect(svc, fx);

        worker.sweep();

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(0);
    }

    @Test
    void sweepSkipsSessionsAlreadyAdvancedByCommands() {
        // RINGING -> ACCEPTED(清除了 expires_at,且状态机已推进)
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");

        worker.sweep();

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.ACCEPTED.name());
    }

    @Test
    void sweepSkipsTerminalSessionsEvenIfExpired() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        worker.sweep();

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(0);
    }

    @Test
    void sweepDoesNotRecreateRecordsForAlreadyExpiredSession() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        fx.session(call.getCallId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));
        worker.sweep();
        long eventsAfterFirst = fx.eventsOf(call.getCallId()).size();

        worker.sweep();

        assertThat(fx.eventsOf(call.getCallId()).size()).isEqualTo(eventsAfterFirst);
        assertThat(fx.eventCount(call.getCallId(), "call.expired")).isEqualTo(1);
        // TTL 事件 id 带时间戳,同一秒内重复扫描仍被守卫式更新拦下
        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.EXPIRED.name());
    }
}