package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.LoginHistory;
import com.douyin.service.LoginDeviceService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/login-history")
public class LoginHistoryController {

    private final LoginDeviceService loginDeviceService;
    private final JwtUtil jwtUtil;

    public LoginHistoryController(LoginDeviceService loginDeviceService, JwtUtil jwtUtil) {
        this.loginDeviceService = loginDeviceService;
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

    /** 获取登录设备列表 */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        List<LoginHistory> list = loginDeviceService.findByUserId(userId, pageNo, pageSize);
        return Result.ok(Map.of("list", list));
    }
}
