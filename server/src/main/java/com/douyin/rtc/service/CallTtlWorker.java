package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.rtc.timeout.CallTimeoutIndex;
import com.douyin.rtc.timeout.CallTimeoutIndexUnavailable;
import com.douyin.rtc.observability.CallControlMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 通话 TTL 回收 worker。生产主路径从分片 Redis ZSET 读取到期项:
 * - RINGING 超过 TTL(默认 180s,可配 rtc.call.ringing-ttl) -> EXPIRED;
 * - NEGOTIATING 超过 TTL(默认 5m,可配 rtc.call.negotiating-ttl) -> FAILED(end_reason=expired)。
 * 全部走守卫式更新,只处理终态前(状态已被客户端命令推进则跳过)。
 */
@Slf4j
@Component
public class CallTtlWorker {

    private static final String TRACE_TTL = "ttl-worker";

    private final CallService callService;
    private final RtcCallSessionMapper sessionMapper;
    private final CallTimeoutIndex timeoutIndex;
    private final int batchSize;
    private volatile boolean recoveryRequired;
    private final CallControlMetrics metrics;
    private volatile Instant nextRecoveryAttempt = Instant.EPOCH;
    private int recoveryFailures;

    /** Unit-test compatibility constructor. Spring uses the Redis-index constructor below. */
    public CallTtlWorker(CallService callService, RtcCallSessionMapper sessionMapper) {
        this(callService, sessionMapper, null, 500, null);
    }

    public CallTtlWorker(CallService callService, RtcCallSessionMapper sessionMapper,
                         CallTimeoutIndex timeoutIndex,
                         @Value("${rtc.call.timeout-batch-size:500}") int batchSize) {
        this(callService, sessionMapper, timeoutIndex, batchSize, null);
    }

    @Autowired
    public CallTtlWorker(CallService callService, RtcCallSessionMapper sessionMapper,
                         CallTimeoutIndex timeoutIndex,
                         @Value("${rtc.call.timeout-batch-size:500}") int batchSize,
                         CallControlMetrics metrics) {
        this.callService = callService;
        this.sessionMapper = sessionMapper;
        this.timeoutIndex = timeoutIndex;
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
        this.recoveryRequired = timeoutIndex != null;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (timeoutIndex != null) {
            recoverIndex();
        }
    }

    @Scheduled(fixedDelayString = "${rtc.call.timeout-poll-ms:1000}", initialDelayString = "5000")
    public void sweep() {
        if (timeoutIndex == null) {
            sweepLegacyForUnitTests();
            return;
        }
        if (recoveryRequired && !recoverIndex()) {
            return;
        }
        Instant nowInstant = Instant.now();
        List<CallTimeoutIndex.DueTimeout> due;
        try {
            due = timeoutIndex.due(nowInstant, batchSize);
        } catch (CallTimeoutIndexUnavailable e) {
            recoveryRequired = true;
            log.warn("[CALL-TIMEOUT] Redis index unavailable; durable calls remain unchanged: {}", e.getMessage());
            return;
        }
        int processed = 0;
        for (CallTimeoutIndex.DueTimeout entry : due) {
            try {
                CallSession before = sessionMapper.findByCallId(entry.callId());
                if (before != null && entry.expectedState().equals(before.getState())
                        && before.getExpiresAt() != null
                        && !before.getExpiresAt().isAfter(LocalDateTime.now())) {
                    String eventId = "sys:ttl:" + entry.callId() + ":" + entry.expectedState()
                            + ":" + before.getStateVersion();
                    callService.expireCall(entry.callId(), eventId, TRACE_TTL);
                    long lagMs = Math.max(0L, nowInstant.toEpochMilli() - entry.expireAt().toEpochMilli());
                    if (metrics != null) {
                        metrics.timeoutLag(lagMs, entry.expectedState());
                    }
                    if (lagMs > 1000L) {
                        log.warn("[CALL-TIMEOUT] lag callId={} state={} lagMs={}",
                                entry.callId(), entry.expectedState(), lagMs);
                    }
                    processed++;
                }
                timeoutIndex.remove(entry.callId());
            } catch (CallTimeoutIndexUnavailable e) {
                recoveryRequired = true;
                break;
            } catch (RuntimeException e) {
                log.warn("[CALL-TIMEOUT] processing failed; entry remains retryable: callId={} error={}",
                        entry.callId(), e.getMessage());
            }
        }
        sweepStuckEnding(LocalDateTime.now());
        if (processed > 0) {
            log.info("[CALL-TIMEOUT] processed={}", processed);
        }
    }

    /** Rebuild after process start or a detected Redis outage, never on the steady-state poll path. */
    public boolean recoverIndex() {
        if (timeoutIndex == null) {
            return false;
        }
        Instant now = Instant.now();
        if (now.isBefore(nextRecoveryAttempt)) {
            return false;
        }
        long afterId = 0L;
        int rebuilt = 0;
        try {
            while (true) {
                List<CallSession> batch = sessionMapper.findTimeoutRecoveryBatch(afterId, batchSize);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (CallSession session : batch) {
                    timeoutIndex.schedule(session.getCallId(), session.getState(),
                            session.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant());
                    afterId = Math.max(afterId, session.getId());
                    rebuilt++;
                }
                if (batch.size() < batchSize) {
                    break;
                }
            }
            recoveryRequired = false;
            recoveryFailures = 0;
            nextRecoveryAttempt = Instant.EPOCH;
            log.info("[CALL-TIMEOUT] index recovery complete: rebuilt={}", rebuilt);
            return true;
        } catch (RuntimeException e) {
            recoveryRequired = true;
            recoveryFailures = Math.min(10, recoveryFailures + 1);
            long delaySeconds = Math.min(60L, 1L << Math.min(6, recoveryFailures));
            nextRecoveryAttempt = Instant.now().plusSeconds(delaySeconds);
            log.warn("[CALL-TIMEOUT] index recovery deferred for {}s: {}", delaySeconds, e.getMessage());
            return false;
        }
    }

    private void sweepLegacyForUnitTests() {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        int failed = 0;
        int ended = 0;
        for (CallSession session : sessionMapper.findExpiredBefore(CallState.RINGING.name(), now)) {
            String eventId = "sys:ttl:" + session.getCallId() + ":" + now.toEpochSecond(ZoneOffset.UTC);
            CallSession after = callService.expireCall(session.getCallId(), eventId, TRACE_TTL);
            if (after != null && CallState.EXPIRED == CallState.valueOf(after.getState())) {
                expired++;
            }
        }
        for (CallSession session : sessionMapper.findExpiredBefore(CallState.NEGOTIATING.name(), now)) {
            String eventId = "sys:ttl:" + session.getCallId() + ":" + now.toEpochSecond(ZoneOffset.UTC) + ":" + UUID.randomUUID();
            CallSession after = callService.expireCall(session.getCallId(), eventId, TRACE_TTL);
            if (after != null && CallState.FAILED == CallState.valueOf(after.getState())) {
                failed++;
            }
        }
        ended = sweepStuckEnding(now);
        if (expired > 0 || failed > 0 || ended > 0) {
            log.info("[CALL-TTL] legacy test sweep: ringingExpired={} negotiatingFailed={} endingEnded={}", expired, failed, ended);
        }
    }

    private int sweepStuckEnding(LocalDateTime now) {
        int ended = 0;
        for (CallSession session : sessionMapper.findEndingStuckBefore(now.minusSeconds(30))) {
            String eventId = "sys:ttl:" + session.getCallId() + ":" + now.toEpochSecond(ZoneOffset.UTC) + ":ended";
            CallSession after = callService.confirmEnded(session.getCallId(), eventId, TRACE_TTL);
            if (after != null && CallState.ENDED == CallState.valueOf(after.getState())) {
                ended++;
            }
        }
        return ended;
    }
}
