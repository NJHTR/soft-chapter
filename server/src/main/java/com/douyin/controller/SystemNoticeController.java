package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.SystemNotice;
import com.douyin.service.SystemNoticeService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-notice")
public class SystemNoticeController {

    private final SystemNoticeService systemNoticeService;
    private final JwtUtil jwtUtil;

    public SystemNoticeController(SystemNoticeService systemNoticeService, JwtUtil jwtUtil) {
        this.systemNoticeService = systemNoticeService;
        this.jwtUtil = jwtUtil;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                return jwtUtil.getUserIdFromToken(token);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** 获取系统通知列表 */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        List<SystemNotice> list = systemNoticeService.findByUserId(userId, pageNo, pageSize);
        int unread = systemNoticeService.countUnread(userId);
        return Result.ok(Map.of("list", list, "unread", unread));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<?> markRead(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        systemNoticeService.markAsRead(id, userId);
        return Result.ok();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<?> markAllRead(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        List<SystemNotice> list = systemNoticeService.findByUserId(userId, 1, 500);
        for (SystemNotice n : list) {
            if (n.getIsRead() == 0) {
                systemNoticeService.markAsRead(n.getId(), userId);
            }
        }
        return Result.ok();
    }
}
