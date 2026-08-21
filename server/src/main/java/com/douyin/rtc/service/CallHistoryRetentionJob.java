package com.douyin.rtc.service;

import com.douyin.rtc.repository.RtcCallEventMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.rtc.repository.RtcCallDeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Bounded terminal-call cleanup. Active calls are never selected or deleted. */
@Slf4j
@Component
public class CallHistoryRetentionJob {

    private final RtcCallSessionMapper sessions;
    private final RtcCallParticipantMapper participants;
    private final RtcCallEventMapper events;
    private final RtcCallDeviceMapper devices;
    private final Duration retention;
    private final int batchSize;

    public CallHistoryRetentionJob(RtcCallSessionMapper sessions,
                                   RtcCallParticipantMapper participants,
                                   RtcCallEventMapper events,
                                   Duration retention,
                                   int batchSize) {
        this(sessions, participants, events, null, retention, batchSize);
    }

    @Autowired
    public CallHistoryRetentionJob(RtcCallSessionMapper sessions,
                                   RtcCallParticipantMapper participants,
                                   RtcCallEventMapper events,
                                   RtcCallDeviceMapper devices,
                                   @Value("${rtc.call.history-retention:31d}") Duration retention,
                                   @Value("${rtc.call.history-purge-batch-size:500}") int batchSize) {
        this.sessions = sessions;
        this.participants = participants;
        this.events = events;
        this.devices = devices;
        this.retention = retention.compareTo(Duration.ofDays(31)) < 0 ? Duration.ofDays(31) : retention;
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
    }

    @Scheduled(fixedDelayString = "${rtc.call.history-purge-interval-ms:3600000}",
            initialDelayString = "${rtc.call.history-purge-initial-delay-ms:60000}")
    @Transactional
    public void purgeTerminalHistory() {
        LocalDateTime olderThan = LocalDateTime.now().minus(retention);
        List<String> callIds = sessions.findTerminalCallIdsForPurge(olderThan, batchSize);
        if (callIds == null || callIds.isEmpty()) {
            return;
        }
        events.deleteByCallIds(callIds);
        if (devices != null) {
            devices.deleteByCallIds(callIds);
        }
        participants.deleteByCallIds(callIds);
        int removed = sessions.deleteTerminalByCallIds(callIds, olderThan);
        log.info("[CALL-HISTORY] purged terminal calls={}", removed);
    }
}
