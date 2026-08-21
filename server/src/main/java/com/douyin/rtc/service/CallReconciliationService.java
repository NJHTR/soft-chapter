package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.websocket.SessionManager;
import com.douyin.rtc.observability.CallControlMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable-call reconciliation for login, reconnect and multi-device convergence. */
@Slf4j
@Service
public class CallReconciliationService {

    private static final int ACTIVE_CALL_LIMIT = 20;

    private final RtcCallSessionMapper sessionMapper;
    private final RtcCallParticipantMapper participantMapper;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final CallControlMetrics metrics;

    public CallReconciliationService(RtcCallSessionMapper sessionMapper,
                                     RtcCallParticipantMapper participantMapper,
                                     SessionManager sessionManager,
                                     ObjectMapper objectMapper) {
        this(sessionMapper, participantMapper, sessionManager, objectMapper, null);
    }

    @Autowired
    public CallReconciliationService(RtcCallSessionMapper sessionMapper,
                                     RtcCallParticipantMapper participantMapper,
                                     SessionManager sessionManager,
                                     ObjectMapper objectMapper,
                                     CallControlMetrics metrics) {
        this.sessionMapper = sessionMapper;
        this.participantMapper = participantMapper;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public Map<String, Object> activeCalls(long userId) {
        List<CallSession> calls = sessionMapper.listActiveByUserId(
                userId, LocalDateTime.now(), ACTIVE_CALL_LIMIT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("server_now", Instant.now());
        result.put("calls", calls == null ? List.of() : calls);
        return result;
    }

    /** Send only to the newly authenticated socket; repeated reconciliation is idempotent by state_version. */
    public void reconcileSession(long userId, WebSocketSession socket) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "rtc.call.reconciliation");
            envelope.putAll(activeCalls(userId));
            sessionManager.push(socket, objectMapper.writeValueAsString(envelope));
            if (metrics != null) {
                metrics.reconciliation(true, "websocket_connect");
            }
        } catch (Exception e) {
            if (metrics != null) {
                metrics.reconciliation(false, "websocket_connect");
            }
            log.warn("[CALL-RECONCILE] delivery failed userId={} sessionId={} error={}",
                    userId, socket.getId(), e.getMessage());
        }
    }

    /** Broadcast a committed authoritative snapshot to every participant device. */
    public void broadcastCall(String callId, String eventId) {
        CallSession call = sessionMapper.findByCallId(callId);
        if (call == null) {
            return;
        }
        List<CallParticipant> participants = participantMapper.listByCall(callId);
        Set<Long> users = new LinkedHashSet<>();
        if (participants != null) {
            for (CallParticipant participant : participants) {
                if (participant != null && participant.getUserId() != null) {
                    users.add(participant.getUserId());
                }
            }
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "rtc.call.state");
            envelope.put("event_id", eventId);
            envelope.put("state_version", call.getStateVersion());
            envelope.put("server_now", Instant.now());
            envelope.put("call", call);
            envelope.put("participants", participants == null ? List.of() : participants);
            String json = objectMapper.writeValueAsString(envelope);
            for (Long userId : users) {
                sessionManager.push(userId, json);
            }
            if (metrics != null) {
                metrics.reconciliation(true, "state_broadcast");
            }
        } catch (Exception e) {
            if (metrics != null) {
                metrics.reconciliation(false, "state_broadcast");
            }
            log.warn("[CALL-RECONCILE] broadcast failed callId={} error={}", callId, e.getMessage());
        }
    }
}
