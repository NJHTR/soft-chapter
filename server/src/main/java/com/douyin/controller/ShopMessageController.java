package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.ShopMessage;
import com.douyin.service.ShopMessageService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shop-message")
public class ShopMessageController {

    private final ShopMessageService shopMessageService;
    private final JwtUtil jwtUtil;

    public ShopMessageController(ShopMessageService shopMessageService, JwtUtil jwtUtil) {
        this.shopMessageService = shopMessageService;
        this.jwtUtil = jwtUtil;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 消息列表 */
    @GetMapping("/list")
    public Result<List<ShopMessage>> list(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(shopMessageService.listByUser(userId));
    }

    /** 未读数量 */
    @GetMapping("/unread")
    public Result<Map<String, Integer>> unread(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        int count = userId == null ? 0 : shopMessageService.unreadCount(userId);
        Map<String, Integer> map = new HashMap<>();
        map.put("count", count);
        return Result.ok(map);
    }

    /** 标记已读 */
    @PutMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        shopMessageService.markRead(id, userId);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        shopMessageService.markAllRead(userId);
        return Result.ok();
    }
}
