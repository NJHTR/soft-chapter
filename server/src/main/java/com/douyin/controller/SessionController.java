package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.ActiveSession;
import com.douyin.service.SessionService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    public SessionController(SessionService sessionService, JwtUtil jwtUtil) {
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    /** 获取当前用户的所有活跃会话列表 */
    @GetMapping("")
    public Result<Map<String, Object>> listSessions(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");

        List<ActiveSession> sessions = sessionService.listUserSessions(userId);

        // 标记当前设备
        String token = extractToken(req);
        Long currentSessionId = token != null ? sessionService.findSessionIdByToken(token) : null;

        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        result.put("currentSessionId", currentSessionId);
        return Result.ok(result);
    }

    /** 撤销单个会话 (踢掉某个设备) */
    @DeleteMapping("/{sessionId}")
    public Result<Void> revokeSession(@PathVariable Long sessionId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");

        // 不能踢掉自己
        String token = extractToken(req);
        Long currentSessionId = token != null ? sessionService.findSessionIdByToken(token) : null;
        if (currentSessionId != null && currentSessionId.equals(sessionId)) {
            return Result.fail(400, "不能退出当前设备，请使用「退出登录」");
        }

        boolean ok = sessionService.revokeSession(sessionId, userId);
        return ok ? Result.ok() : Result.fail(404, "会话不存在或已失效");
    }

    /** 一键登出所有其他设备 */
    @DeleteMapping("/all-others")
    public Result<Map<String, Object>> revokeAllOther(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");

        String token = extractToken(req);
        Long currentSessionId = token != null ? sessionService.findSessionIdByToken(token) : null;
        if (currentSessionId == null) return Result.fail(400, "无法识别当前会话");

        int count = sessionService.revokeAllOtherSessions(userId, currentSessionId);
        return Result.ok(Map.of("revokedCount", count));
    }

    // ==================== 工具 ====================

    private Long getLoginUserId(HttpServletRequest req) {
        String token = extractToken(req);
        if (token == null) return null;
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
}
