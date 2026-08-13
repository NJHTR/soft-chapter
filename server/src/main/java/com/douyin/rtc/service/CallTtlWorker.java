package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 通话 TTL 回收 worker。每 10 秒扫描:
 * - RINGING 超过 TTL(默认 30s,可配 rtc.call.ringing-ttl) -> EXPIRED;
 * - NEGOTIATING 超过 TTL(默认 5m,可配 rtc.call.negotiating-ttl) -> FAILED(end_reason=expired)。
 * 全部走守卫式更新,只处理终态前(状态已被客户端命令推进则跳过)。
 */
@Slf4j
@Component
public class CallTtlWorker {

    private static final String TRACE_TTL = "ttl-worker";

    private final CallService callService;
    private final RtcCallSessionMapper sessionMapper;

    public CallTtlWorker(CallService callService, RtcCallSessionMapper sessionMapper) {
        this.callService = callService;
        this.sessionMapper = sessionMapper;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        int failed = 0;
        for (CallSession session : sessionMapper.findExpiredBefore(CallState.RINGING.name(), now)) {
            String eventId = "ttl:" + session.getCallId() + ":" + now.toEpochSecond(ZoneOffset.UTC);
            CallSession after = callService.expireCall(session.getCallId(), eventId, TRACE_TTL);
            if (after != null && CallState.EXPIRED == CallState.valueOf(after.getState())) {
                expired++;
            }
        }
        for (CallSession session : sessionMapper.findExpiredBefore(CallState.NEGOTIATING.name(), now)) {
            String eventId = "ttl:" + session.getCallId() + ":" + now.toEpochSecond(ZoneOffset.UTC) + ":" + UUID.randomUUID();
            CallSession after = callService.expireCall(session.getCallId(), eventId, TRACE_TTL);
            if (after != null && CallState.FAILED == CallState.valueOf(after.getState())) {
                failed++;
            }
        }
        if (expired > 0 || failed > 0) {
            log.info("[CALL-TTL] sweep done: ringingExpired={} negotiatingFailed={}", expired, failed);
        }
    }
}