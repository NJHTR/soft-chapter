package com.douyin.rtc;

import com.douyin.rtc.repository.RtcCallEventMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.rtc.service.CallHistoryRetentionJob;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallHistoryRetentionJobTest {

    @Test
    void purgesChildrenBeforeTerminalSessions() {
        RtcCallSessionMapper sessions = mock(RtcCallSessionMapper.class);
        RtcCallParticipantMapper participants = mock(RtcCallParticipantMapper.class);
        RtcCallEventMapper events = mock(RtcCallEventMapper.class);
        when(sessions.findTerminalCallIdsForPurge(any(), eq(500)))
                .thenReturn(List.of("call-1", "call-2"));
        CallHistoryRetentionJob job = new CallHistoryRetentionJob(
                sessions, participants, events, Duration.ofDays(31), 500);

        job.purgeTerminalHistory();

        InOrder order = inOrder(events, participants, sessions);
        order.verify(events).deleteByCallIds(List.of("call-1", "call-2"));
        order.verify(participants).deleteByCallIds(List.of("call-1", "call-2"));
        order.verify(sessions).deleteTerminalByCallIds(eq(List.of("call-1", "call-2")), any(LocalDateTime.class));
    }

    @Test
    void emptyBatchDoesNotDeleteAnything() {
        RtcCallSessionMapper sessions = mock(RtcCallSessionMapper.class);
        RtcCallParticipantMapper participants = mock(RtcCallParticipantMapper.class);
        RtcCallEventMapper events = mock(RtcCallEventMapper.class);
        when(sessions.findTerminalCallIdsForPurge(any(), eq(500))).thenReturn(List.of());
        CallHistoryRetentionJob job = new CallHistoryRetentionJob(
                sessions, participants, events, Duration.ofDays(31), 500);

        job.purgeTerminalHistory();

        verify(events, never()).deleteByCallIds(any());
        verify(participants, never()).deleteByCallIds(any());
        verify(sessions, never()).deleteTerminalByCallIds(any(), any());
    }
}
