package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.p2p.P2pCallContextPort;
import com.douyin.rtc.p2p.P2pCandidateClassification;
import com.douyin.rtc.p2p.P2pFallbackReason;
import com.douyin.rtc.p2p.P2pService;
import com.douyin.rtc.p2p.P2pSignalingEnvelope;
import com.douyin.rtc.p2p.P2pStatusView;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 受控 1 对 1 P2P 控制面(契约第 7 节,默认 fail-closed)。
 * 独立版本化信令 port 只转发 SDP/ICE 控制数据;原始 payload 不进任何持久化。
 */
@Slf4j
@RestController
@RequestMapping("/api/rtc/p2p")
public class P2pController {

    private final P2pService p2pService;
    private final P2pCallContextPort callContext;
    private final JwtUtil jwtUtil;

    public P2pController(P2pService p2pService, P2pCallContextPort callContext, JwtUtil jwtUtil) {
        this.p2pService = p2pService;
        this.callContext = callContext;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/call/{callId}/topology")
    public Result<?> topology(@PathVariable String callId, HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        return Result.ok(p2pService.status(callId, generation));
    }

    @PostMapping("/call/{callId}/evaluate")
    public Result<?> evaluate(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                              HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        P2pStatusView view = p2pService.evaluate(callId, generation, callContext.eligibilityContext(callId),
                eventId(body), actor);
        return Result.ok(view);
    }

    @PostMapping("/call/{callId}/consent")
    public Result<?> consent(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                             HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        String action = asString(body, "action", "consent");
        P2pStatusView view;
        if ("revoke".equals(action)) {
            view = p2pService.revokeConsent(callId, generation, actor, eventId(body));
        } else {
            view = p2pService.consent(callId, generation, actor, eventId(body));
        }
        return Result.ok(view);
    }

    @PostMapping("/call/{callId}/probe-start")
    public Result<?> probeStart(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        return Result.ok(p2pService.probeStart(callId, generation, actor, eventId(body)));
    }

    @PostMapping("/call/{callId}/probe-result")
    public Result<?> probeResult(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                 HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        String rawOutcome = asString(body, "outcome", "");
        P2pCandidateClassification.ProbeOutcome outcome;
        try {
            outcome = P2pCandidateClassification.ProbeOutcome.valueOf(rawOutcome.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "outcome 必须为 DIRECT|SRFLX|RELAY|SFU");
        }
        P2pStatusView view = p2pService.probeResult(callId, generation, outcome,
                asString(body, "local_candidate_type", null), asString(body, "remote_candidate_type", null),
                asLong(body, "rtt_ms").orElse(null), asLong(body, "loss_pct").orElse(null), eventId(body), actor);
        return Result.ok(view);
    }

    @PostMapping("/call/{callId}/fallback")
    public Result<?> fallback(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                              HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        P2pFallbackReason reason;
        try {
            reason = P2pFallbackReason.valueOf(asString(body, "reason", ""));
        } catch (IllegalArgumentException e) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "reason 必须为稳定枚举");
        }
        return Result.ok(p2pService.fallback(callId, generation, reason, eventId(body), actor));
    }

    @PostMapping("/call/{callId}/signal")
    public Result<?> relaySignal(@PathVariable String callId, @RequestBody Map<String, Object> body,
                                 HttpServletRequest req) {
        Long actor = loginUserId(req);
        long generation = callContext.topologyGeneration(callId);
        P2pSignalingEnvelope envelope = new P2pSignalingEnvelope();
        envelope.setVersion(asString(body, "version", "v1"));
        envelope.setCallId(asString(body, "call_id", callId));
        envelope.setTopologyGeneration(asLong(body, "topology_generation").orElse(generation));
        envelope.setFromUserId(longValue(body, "from_user_id"));
        envelope.setToUserId(longValue(body, "to_user_id"));
        envelope.setKind(asString(body, "kind", ""));
        envelope.setSeq(asLong(body, "seq").orElse(0L));
        envelope.setEventId(asString(body, "event_id", ""));
        envelope.setCreatedEpochMs(asLong(body, "created_epoch_ms").orElse(0L));
        Object payloadObj = body.get("payload");
        envelope.setPayload(payloadObj == null ? null : String.valueOf(payloadObj));
        envelope.setCandidateCount(((Number) asLong(body, "candidate_count").orElse(0L)).intValue());
        envelope.setSignature(asString(body, "signature", ""));
        long seq = p2pService.relaySignal(callId, generation, envelope, actor, callContext.participantIds(callId));
        return Result.ok(Map.of("seq", seq));
    }

    @GetMapping("/call/{callId}/signal/inbox")
    public Result<?> inbox(@PathVariable String callId, HttpServletRequest req) {
        Long actor = loginUserId(req);
        return Result.ok(p2pService.takeInbox(callId, actor));
    }

    @GetMapping("/call/{callId}/audit")
    public Result<?> audit(@PathVariable String callId, HttpServletRequest req) {
        loginUserId(req);
        return Result.ok(p2pService.audit(callId));
    }

    // ===== helpers =====

    private Long loginUserId(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "请先登录");
        }
        return userId;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserIdFromToken(auth.substring(7));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String eventId(Map<String, Object> body) {
        return asString(body, "event_id", "");
    }

    private static String asString(Map<String, Object> body, String key, String def) {
        if (body == null) {
            return def;
        }
        Object v = body.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static java.util.Optional<Long> asLong(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return java.util.Optional.empty();
        }
        Object v = body.get(key);
        if (v instanceof Number n) {
            return java.util.Optional.of(n.longValue());
        }
        return java.util.Optional.of(Long.parseLong(String.valueOf(v)));
    }

    private static Long longValue(Map<String, Object> body, String key) {
        return asLong(body, key).orElse(null);
    }
}