package com.douyin.rtc;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CallTtlWorker;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import com.douyin.rtc.timeout.CallTimeoutIndex;
import com.douyin.rtc.timeout.CallTimeoutIndexUnavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTimeoutIndexWorkerTest {

    private RtcRepoFixture fixture;
    private CallService service;
    private CallTimeoutIndex index;

    @BeforeEach
    void setUp() {
        fixture = new RtcRepoFixture();
        service = CallTestSupport.service(fixture);
        index = mock(CallTimeoutIndex.class);
    }

    @Test
    void dueRedisEntryExpiresThroughDatabaseCas() {
        CallSession call = CallTestSupport.createDirect(service, fixture);
        call.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        Instant deadline = call.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        when(index.due(any(), anyInt())).thenReturn(List.of(
                new CallTimeoutIndex.DueTimeout(call.getCallId(), CallState.RINGING.name(), deadline)));

        CallTtlWorker worker = new CallTtlWorker(service, fixture.sessions, index, 100);
        worker.sweep();

        assertThat(call.getState()).isEqualTo(CallState.EXPIRED.name());
        verify(index).remove(call.getCallId());
    }

    @Test
    void startupRecoveryRebuildsOnlyDurableActiveTimeouts() {
        CallSession call = CallTestSupport.createDirect(service, fixture);
        CallTtlWorker worker = new CallTtlWorker(service, fixture.sessions, index, 100);

        assertThat(worker.recoverIndex()).isTrue();

        verify(index).schedule(call.getCallId(), CallState.RINGING.name(),
                call.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant());
        verify(fixture.sessions, never()).findExpiredBefore(anyString(), any());
    }

    @Test
    void redisFailureDoesNotScanMysqlOrMutateDurableCall() {
        CallSession call = CallTestSupport.createDirect(service, fixture);
        when(index.due(any(), anyInt())).thenThrow(
                new CallTimeoutIndexUnavailable("redis down", new RuntimeException("down")));
        doThrow(new CallTimeoutIndexUnavailable("redis down", new RuntimeException("down")))
                .when(index).schedule(anyString(), anyString(), any());

        CallTtlWorker worker = new CallTtlWorker(service, fixture.sessions, index, 100);
        worker.sweep();

        assertThat(call.getState()).isEqualTo(CallState.RINGING.name());
        verify(fixture.sessions, never()).findExpiredBefore(anyString(), any());
    }
}
