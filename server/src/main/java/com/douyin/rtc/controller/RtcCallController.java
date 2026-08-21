package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.provider.LiveKitTokenService;
import com.douyin.rtc.provider.TokenResult;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CreateCallCommand;
import com.douyin.rtc.service.CallReconciliationService;
import com.douyin.rtc.webhook.LiveKitWebhookService;
import com.douyin.rtc.webhook.WebhookSignatureVerifier;
import com.douyin.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RTC 控制面 REST 入口 (rtc-signaling)。
 *
 * <p>与 MessageController 同惯例: userId 从 Authorization Bearer token 解析
 * (SessionFilter 只做会话活性校验,业务鉴权在 CallService/AclService)。
 * /api/rtc/webhook/livekit 在 SessionFilter 白名单中,用 LiveKit 签名鉴权。
 */
@Slf4j
@RestController
@RequestMapping("/api/rtc")
public class RtcCallController {

    private final JwtUtil jwtUtil;
    private final CallService callService;
    private final LiveKitTokenService tokenService;
    private final LiveKitWebhookService webhookService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final CallReconciliationService reconciliationService;

    public RtcCallController(JwtUtil jwtUtil, CallService callService,
                             LiveKitTokenService tokenService,
                             LiveKitWebhookService webhookService,
                             WebhookSignatureVerifier signatureVerifier,
                             ObjectMapper objectMapper) {
        this(jwtUtil, callService, tokenService, webhookService, signatureVerifier,
                objectMapper, null);
    }

    @Autowired
    public RtcCallController(JwtUtil jwtUtil, CallService callService,
                             LiveKitTokenService tokenService,
                             LiveKitWebhookService webhookService,
                             WebhookSignatureVerifier signatureVerifier,
                             ObjectMapper objectMapper,
                             CallReconciliationService reconciliationService) {
        this.jwtUtil = jwtUtil;
        this.callService = callService;
        this.tokenService = tokenService;
        this.webhookService = webhookService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.reconciliationService = reconciliationService;
    }

    // ==================== LiveKit token ====================

    /**
     * 签发 LiveKit AccessToken。body: {call_id, ttl_seconds?, trace_id?};
     * actorId 由登录上下文解析,房间名/发布订阅权限服务端决定(宪法 §2.4/§2.7)。
     */
    @PostMapping("/token")
    public Result<Map<String, Object>> token(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long actorId = getLoginUserId(req);
        TokenResult issued = tokenService.issue(
                string(body, "call_id"), actorId, integer(body, "ttl_seconds"), string(body, "trace_id"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", issued.token());
        out.put("room_name", issued.roomName());
        out.put("call_id", issued.callId());
        out.put("identity", String.valueOf(issued.userId()));
        out.put("ttl_seconds", issued.ttlSeconds());
        out.put("expires_at", issued.expiresAt());
        return Result.ok(out);
    }

    /** LiveKit webhook 接收(白名单 + 官方 JWT/迁移 HMAC 签名鉴权,不走登录)。 */
    @PostMapping("/webhook/livekit")
    public ResponseEntity<Result<?>> webhook(@RequestBody byte[] raw,
                                             @RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestHeader(value = "LiveKit-Signature", required = false) String legacySignature) {
        if (!signatureVerifier.verify(authorization, legacySignature, raw)) {
            log.warn("[RTC-WEBHOOK] signature verification failed");
            return ResponseEntity.status(401).body(Result.fail(401, "LiveKit webhook 签名校验失败"));
        }
        try {
            String json = new String(raw, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            String eventId = string(payload, "id");
            String eventType = string(payload, "event");
            String roomName = null;
            Object room = payload.get("room");
            if (room instanceof Map<?, ?> m && m.get("name") != null) {
                roomName = String.valueOf(m.get("name"));
            }
            LiveKitWebhookService.WebhookHandleResult result =
                    webhookService.handle(eventId, eventType, roomName, payload, json);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("handled", result.handled());
            out.put("duplicate", result.duplicate());
            return ResponseEntity.ok(Result.ok(out));
        } catch (Exception e) {
            log.warn("[RTC-WEBHOOK] malformed payload rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.fail(400, "webhook payload 解析失败"));
        }
    }

    // ==================== 通话生命周期 ====================

    /** 创建通话。body: {scope, mode?, target_user_ids[]|target_user_id|group_id, client_request_id, event_id?, trace_id?} */
    @PostMapping("/call")
    public Result<CallSession> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "请先登录");
        }
        String scope = string(body, "scope");
        Long targetUserId = firstTarget(body);
        Long groupId = longValue(body.get("group_id"));
        if (scope == null || !(scope.equals("direct") || scope.equals("group"))) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "scope 仅支持 direct/group");
        }
        CreateCallCommand cmd = new CreateCallCommand(
                userId, scope, targetUserId, groupId,
                string(body, "mode"), string(body, "provider"),
                string(body, "client_request_id"), string(body, "event_id"), string(body, "trace_id"));
        return Result.ok(callService.createCall(cmd));
    }

    /** Login/reconnect source of truth; WebSocket delivery is only an optimization. */
    @GetMapping("/calls/active")
    public Result<Map<String, Object>> activeCalls(HttpServletRequest req) {
        return Result.ok(reconciliationService.activeCalls(loginUserId(req)));
    }

    @PostMapping("/call/{callId}/accept")
    public Result<CallSession> accept(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest req) {
        return Result.ok(callService.acceptCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    @PostMapping("/call/{callId}/reject")
    public Result<CallSession> reject(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest req) {
        return Result.ok(callService.rejectCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    @PostMapping("/call/{callId}/cancel")
    public Result<CallSession> cancel(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest req) {
        return Result.ok(callService.cancelCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    @PostMapping("/call/{callId}/hangup")
    public Result<CallSession> hangup(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest req) {
        return Result.ok(callService.hangupCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    @PostMapping("/call/{callId}/join")
    public Result<CallSession> join(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest req) {
        return Result.ok(callService.joinCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    /** 客户端媒体连接成功确认。仅限通话参与者，用于 webhook 延迟或不可达时收敛状态。 */
    @PostMapping("/call/{callId}/connected")
    public Result<CallSession> connected(@PathVariable String callId,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         HttpServletRequest req) {
        return Result.ok(callService.confirmConnected(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    @PostMapping("/call/{callId}/leave")
    public Result<CallSession> leave(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest req) {
        return Result.ok(callService.leaveCall(callId, loginUserId(req), eventId(body), traceId(body)));
    }

    /** 通话详情: 会话 + 参与者 + 事件账本(宪法 §2.8 可观测性),仅限登录的参与者。 */
    @GetMapping("/call/{callId}")
    public Result<Map<String, Object>> detail(@PathVariable String callId, HttpServletRequest req) {
        Long userId = loginUserId(req);
        CallSession session = callService.getCall(callId);
        if (session == null) {
            throw new CallDomainException(CallErrorCode.SESSION_NOT_FOUND, "通话不存在: " + callId);
        }
        if (callService.getParticipant(callId, userId) == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "不是通话参与者");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("call", session);
        out.put("participants", callService.participants(callId));
        out.put("events", callService.events(callId));
        return Result.ok(out);
    }

    // ==================== 工具 ====================

    private Long loginUserId(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "请先登录");
        }
        return userId;
    }

    /** 与 MessageController 相同的 userId 解析惯例(Authorization Bearer)。 */
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

    private Long firstTarget(Map<String, Object> body) {
        Object array = body.get("target_user_ids");
        if (array instanceof List<?> list && !list.isEmpty()) {
            return longValue(list.get(0));
        }
        return longValue(body.get("target_user_id"));
    }

    private static String string(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private static Integer integer(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "参数 " + key + " 必须是数字");
        }
    }

    private static Long longValue(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "参数必须是数字");
        }
    }

    private static String eventId(Map<String, Object> body) {
        return body != null ? string(body, "event_id") : null;
    }

    private static String traceId(Map<String, Object> body) {
        return body != null ? string(body, "trace_id") : null;
    }
}
