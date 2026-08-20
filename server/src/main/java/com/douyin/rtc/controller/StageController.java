package com.douyin.rtc.controller;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.stage.StageAuditEntry;
import com.douyin.rtc.stage.StageCommand;
import com.douyin.rtc.stage.StageEvent;
import com.douyin.rtc.stage.StageMember;
import com.douyin.rtc.stage.StageService;
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
 * Stage 控制面(契约 §6)。所有命令携带 event_id(CAS 由服务端 generation 校验);
 * 媒体不经过本 controller,观众路径始终为 SRS WHEP/LL-HLS/HLS/HTTP-FLV/CDN。
 */
@Slf4j
@RestController
@RequestMapping("/api/live/stage")
public class StageController {

    private final StageService stageService;
    private final JwtUtil jwtUtil;

    public StageController(StageService stageService, JwtUtil jwtUtil) {
        this.stageService = stageService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/request")
    public Result<?> request(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long userId = loginUserId(req);
        long liveId = requiredLong(body, "live_id");
        String eventId = requiredString(body, "event_id");
        return Result.ok(snapshot(stageService.apply(new StageEvent(
                StageCommand.REQUEST, liveId, userId, userId,
                eventId, currentGeneration(stageService, liveId, userId), System.currentTimeMillis()))));
    }

    @PostMapping("/approve")
    public Result<?> approve(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        String eventId = requiredString(body, "event_id");
        return Result.ok(snapshot(stageService.apply(command(StageCommand.APPROVE, liveId, target,
                loginUserId(req), eventId))));
    }

    @PostMapping("/demote")
    public Result<?> demote(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        String eventId = requiredString(body, "event_id");
        return Result.ok(snapshot(stageService.apply(command(StageCommand.DEMOTE, liveId, target,
                loginUserId(req), eventId))));
    }

    @PostMapping("/revoke")
    public Result<?> revoke(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        String eventId = requiredString(body, "event_id");
        return Result.ok(snapshot(stageService.apply(command(StageCommand.REVOKE, liveId, target,
                loginUserId(req), eventId))));
    }

    /** provider 已确认成员入房(受控确认,生成 stage.joined 事件)。 */
    @PostMapping("/joined")
    public Result<?> joined(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        String eventId = requiredString(body, "event_id");
        return Result.ok(snapshot(stageService.apply(command(StageCommand.JOINED, liveId, target,
                loginUserId(req), eventId))));
    }

    /** provider 已确认发布权限移除(REVOKING -> REVOKED)。 */
    @PostMapping("/confirm-revoked")
    public Result<?> confirmRevoked(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        long generation = requiredLong(body, "generation");
        loginUserId(req);
        return Result.ok(snapshot(stageService.confirmRevoked(liveId, target,
                requiredString(body, "event_id"), generation)));
    }

    /** provider 已确认离开/断开(DEMOTING -> AUDIENCE)。 */
    @PostMapping("/confirm-left")
    public Result<?> confirmLeft(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        long target = requiredLong(body, "target_user_id");
        long generation = requiredLong(body, "generation");
        loginUserId(req);
        return Result.ok(snapshot(stageService.confirmLeft(liveId, target,
                requiredString(body, "event_id"), generation)));
    }

    @PostMapping("/egress")
    public Result<?> egress(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long liveId = requiredLong(body, "live_id");
        String roomName = requiredString(body, "room_name");
        return Result.ok(Map.of("egress_ref",
                stageService.requestStageEgress(liveId, roomName, loginUserId(req),
                        requiredString(body, "event_id"))));
    }

    @GetMapping("/{liveId}/members")
    public Result<?> members(@PathVariable long liveId) {
        return Result.ok(stageService.members(liveId));
    }

    @GetMapping("/{liveId}/audit")
    public Result<?> audit(@PathVariable long liveId) {
        List<StageAuditEntry> entries = stageService.audit(liveId, 100);
        return Result.ok(entries);
    }

    // ==================== 工具 ====================

    private StageEvent command(StageCommand command, long liveId, long target,
                               long actorUserId, String eventId) {
        return new StageEvent(command, liveId, target, actorUserId, eventId,
                currentGeneration(stageService, liveId, target), System.currentTimeMillis());
    }

    /** 当前成员 generation(客户端可先用 GET members 读取;服务端永远以当前值为准)。 */
    private long currentGeneration(StageService svc, long liveId, long userId) {
        return svc.members(liveId).stream()
                .filter(m -> m.userId() == userId)
                .map(StageMember::generation)
                .findFirst()
                .orElse(0L);
    }

    private Map<String, Object> snapshot(StageMember m) {
        return Map.of(
                "live_id", m.liveId(),
                "user_id", m.userId(),
                "status", m.status().name(),
                "generation", m.generation(),
                "may_publish", m.mayPublish());
    }

    private long loginUserId(HttpServletRequest req) {
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

    private static long requiredLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                // fall through to invalid argument
            }
        }
        throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "参数 " + key + " 必须是数字");
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            return String.valueOf(v);
        }
        throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少参数 " + key);
    }
}
