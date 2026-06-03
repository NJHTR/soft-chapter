package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.dto.UpdateProfileDTO;
import com.douyin.entity.User;
import com.douyin.service.UserService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.UserVO;
import com.douyin.vo.VideoVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final VideoService videoService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, VideoService videoService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.videoService = videoService;
        this.jwtUtil = jwtUtil;
    }

    /** 从请求头提取当前登录用户, 未登录返回 null */
    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                return jwtUtil.getUserIdFromToken(token);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 用户面板信息 */
    @GetMapping("/panel")
    public Result<UserVO> panel(@RequestParam(required = false) Long uid, HttpServletRequest req) {
        Long loginUserId = getLoginUserId(req);
        Long queryUid = uid != null ? uid : loginUserId;

        User user = null;
        if (queryUid != null) {
            user = userService.getById(queryUid);
        }

        // 未登录且未指定uid: 返回访客用户
        if (user == null) {
            user = new User();
            user.setNickname("点击登录");
            user.setUniqueId("");
            user.setSignature("登录后查看更多精彩内容");
        }
        return Result.ok(UserVO.from(user));
    }

    /** 好友列表 */
    @GetMapping("/friends")
    public Result<Object> friends() {
        return Result.ok(userService.getFriends());
    }

    /** 用户信息 */
    @GetMapping("/userinfo")
    public Result<UserVO> userinfo(@RequestParam(required = false) Long uid, HttpServletRequest req) {
        Long loginUserId = getLoginUserId(req);
        Long queryUid = uid != null ? uid : loginUserId;

        if (queryUid != null) {
            User user = userService.getById(queryUid);
            if (user != null) {
                return Result.ok(UserVO.from(user));
            }
        }
        return Result.fail("用户不存在");
    }

    /** 某用户的视频列表 */
    @GetMapping("/video_list")
    public Result<PageDTO<VideoVO>> videoList(
            @RequestParam Long id,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getUserVideos(viewerUserId, id, pageNo, pageSize));
    }

    /** 用户收藏 */
    @GetMapping("/collect")
    public Result<Map<String, Object>> collect() {
        return Result.ok(Map.of(
                "video", Map.of("total", 0, "list", java.util.Collections.emptyList()),
                "music", Map.of("total", 0, "list", java.util.Collections.emptyList())
        ));
    }

    /** 更新用户资料 */
    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody UpdateProfileDTO dto, HttpServletRequest req) {
        Long uid = getLoginUserId(req);
        if (uid == null) return Result.fail(401, "请先登录");
        try {
            userService.updateProfile(uid, dto.getNickname(), dto.getSignature(),
                    dto.getGender(), dto.getBirthday(), dto.getProvince(), dto.getCity());
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 更新头像 */
    @PutMapping("/avatar")
    public Result<?> updateAvatar(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long uid = getLoginUserId(req);
        if (uid == null) return Result.fail(401, "请先登录");
        String url = body.get("url");
        if (url == null || url.isEmpty()) return Result.fail("url不能为空");
        userService.updateAvatar(uid, url);
        return Result.ok();
    }

    /** 切换关注 */
    @PostMapping("/follow/{userId}")
    public Result<Map<String, Object>> toggleFollow(@PathVariable Long userId, HttpServletRequest req) {
        Long loginUserId = getLoginUserId(req);
        if (loginUserId == null) return Result.fail("请先登录");
        boolean followed = userService.toggleFollow(loginUserId, userId);
        return Result.ok(Map.of("isAttention", followed));
    }

    /** 更新背景图 */
    @PutMapping("/cover")
    public Result<?> updateCover(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long uid = getLoginUserId(req);
        if (uid == null) return Result.fail(401, "请先登录");
        String url = body.get("url");
        if (url == null || url.isEmpty()) return Result.fail("url不能为空");
        userService.updateCover(uid, url);
        return Result.ok();
    }
}
