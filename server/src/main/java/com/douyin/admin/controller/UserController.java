package com.douyin.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.User;
import com.douyin.mapper.UserMapper;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController("AdminUserController")
@RequestMapping("/api/admin")
public class UserController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public UserController(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    private User checkAdmin(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.getUserIdFromToken(auth.substring(7));
                if (userId != null) {
                    User user = userMapper.selectById(userId);
                    if (user != null && "ADMIN".equals(user.getRole())) return user;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ========== User Management ==========

    @GetMapping("/users/list")
    public Result<PageDTO<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role.toUpperCase());
        }
        wrapper.orderByDesc(User::getCreateTime);

        IPage<User> page = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        var voList = page.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("uid", u.getUid());
            m.put("nickname", u.getNickname());
            m.put("email", u.getEmail());
            m.put("avatar", u.getAvatar168Url());
            m.put("role", u.getRole());
            m.put("status", (u.getIsDelete() != null && u.getIsDelete() == 1) ? "BANNED" : "ACTIVE");
            m.put("followerCount", u.getFollowerCount());
            m.put("createTime", u.getCreateTime());
            return m;
        }).toList();

        return Result.ok(new PageDTO<>(page.getTotal(), pageNo, pageSize, voList));
    }

    @PutMapping("/users/{uid}/role")
    public Result<?> changeRole(@PathVariable Long uid, @RequestBody Map<String, String> body,
                                HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("No admin permission");

        User user = userMapper.selectById(uid);
        if (user == null) return Result.fail("User not found");
        if ("ADMIN".equals(user.getRole())) return Result.fail("Cannot change admin role");

        String newRole = body.get("role");
        if (newRole == null || newRole.isEmpty()) return Result.fail("role is required");

        user.setRole(newRole.toUpperCase());
        userMapper.updateById(user);

        log.info("Admin {} changed user {} role to {}", admin.getUid(), uid, newRole);
        return Result.ok(Map.of("uid", uid, "role", newRole));
    }

    @PutMapping("/users/{uid}/ban")
    public Result<?> ban(@PathVariable Long uid, HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("No admin permission");

        User user = userMapper.selectById(uid);
        if (user == null) return Result.fail("User not found");
        if ("ADMIN".equals(user.getRole())) return Result.fail("Cannot ban admin");

        user.setIsDelete(1);
        userMapper.updateById(user);

        log.info("Admin {} banned user {}", admin.getUid(), uid);
        return Result.ok(Map.of("uid", uid, "status", "BANNED"));
    }

    @PutMapping("/users/{uid}/unban")
    public Result<?> unban(@PathVariable Long uid, HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("No admin permission");

        User user = userMapper.selectById(uid);
        if (user == null) return Result.fail("User not found");

        user.setIsDelete(0);
        userMapper.updateById(user);

        log.info("Admin {} unbanned user {}", admin.getUid(), uid);
        return Result.ok(Map.of("uid", uid, "status", "ACTIVE"));
    }
}
