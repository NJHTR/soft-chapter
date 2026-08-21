package com.douyin.rtc;

import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static org.assertj.core.api.Assertions.assertThat;

class CallLifecycleRaceTest {

    private RtcRepoFixture fixture;
    private CallService service;

    @BeforeEach
    void setUp() {
        fixture = new RtcRepoFixture();
        service = CallTestSupport.service(fixture);
    }

    @Test
    void stateAndEventVersionsAdvanceWithAuthoritativeTransitions() {
        CallSession call = CallTestSupport.createDirect(service, fixture);

        assertThat(call.getState()).isEqualTo(CallState.RINGING.name());
        assertThat(call.getStateVersion()).isEqualTo(1L);
        assertThat(call.getRingAt()).isNotNull();
        assertThat(fixture.eventsOf(call.getCallId()))
                .extracting(CallEvent::getEventVersion)
                .containsExactly(1L);

        service.acceptCall(call.getCallId(), CALLEE, "evt-accept-version", "trace");

        assertThat(call.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(call.getStateVersion()).isEqualTo(2L);
        assertThat(fixture.eventsOf(call.getCallId()))
                .extracting(CallEvent::getEventVersion)
                .containsExactly(1L, 2L);
    }

    @Test
    void acceptBeforeDeadlineWinsAndLateTimeoutCannotOverwrite() {
        CallSession call = CallTestSupport.createDirect(service, fixture);
        call.setExpiresAt(LocalDateTime.now().plusNanos(100_000_000));

        service.acceptCall(call.getCallId(), CALLEE, "evt-accept-before", "trace");
        CallSession timeout = service.expireCall(call.getCallId(), "sys:ttl:late", "ttl-worker");

        assertThat(timeout.getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(timeout.getStateVersion()).isEqualTo(2L);
        assertThat(fixture.eventCount(call.getCallId(), "call.expired")).isZero();
    }

    @Test
    void timeoutFirstWinsAndLateAcceptConvergesToTerminalFact() {
        CallSession call = CallTestSupport.createDirect(service, fixture);
        call.setExpiresAt(LocalDateTime.now().minusNanos(100_000_000));

        service.expireCall(call.getCallId(), "sys:ttl:first", "ttl-worker");
        CallSession lateAccept = service.acceptCall(call.getCallId(), CALLEE, "evt-accept-late", "trace");

        assertThat(lateAccept.getState()).isEqualTo(CallState.EXPIRED.name());
        assertThat(lateAccept.getStateVersion()).isEqualTo(2L);
        assertThat(fixture.eventCount(call.getCallId(), "call.accept")).isZero();
        assertThat(fixture.eventCount(call.getCallId(), "call.expired")).isEqualTo(1L);
    }

    @Test
    void cancelAndAcceptHaveOneCasWinner() {
        CallSession call = CallTestSupport.createDirect(service, fixture);

        service.cancelCall(call.getCallId(), INITIATOR, "evt-cancel-first", "trace");
        CallSession accept = service.acceptCall(call.getCallId(), CALLEE, "evt-accept-second", "trace");

        assertThat(accept.getState()).isEqualTo(CallState.CANCELLED.name());
        assertThat(fixture.eventCount(call.getCallId(), "call.cancel")).isEqualTo(1L);
        assertThat(fixture.eventCount(call.getCallId(), "call.accept")).isZero();
    }
}
