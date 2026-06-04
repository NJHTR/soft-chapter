package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.dto.UpdateProfileDTO;
import com.douyin.entity.Follow;
import com.douyin.entity.User;
import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.mapper.FollowMapper;
import com.douyin.service.UserService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.NotificationVO;
import com.douyin.vo.UserVO;
import com.douyin.vo.VideoVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final VideoService videoService;
    private final JwtUtil jwtUtil;
    private final FollowMapper followMapper;
    private final MessagePublisher messagePublisher;

    public UserController(UserService userService, VideoService videoService, JwtUtil jwtUtil,
                          FollowMapper followMapper, MessagePublisher messagePublisher) {
        this.userService = userService;
        this.videoService = videoService;
        this.jwtUtil = jwtUtil;
        this.followMapper = followMapper;
        this.messagePublisher = messagePublisher;
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

        log.info("panel called: uid={}, loginUserId={}, queryUid={}", uid, loginUserId, queryUid);

        User user = null;
        if (queryUid != null) {
            user = userService.getById(queryUid);
            log.info("panel user query: queryUid={}, userFound={}", queryUid, user != null);
            if (user != null) {
                long followerCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowId, queryUid));
                long followingCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, queryUid));
                log.info("panel counts: followerCount={}, followingCount={}", followerCount, followingCount);
                user.setFollowerCount(followerCount);
                user.setFollowingCount(followingCount);
            }
        }

        // 未登录且未指定uid: 返回访客用户
        if (user == null) {
            log.info("panel: user is null, returning guest");
            user = new User();
            user.setNickname("点击登录");
            user.setUniqueId("");
            user.setSignature("登录后查看更多精彩内容");
        }

        UserVO vo = UserVO.from(user);
        log.info("panel response: follower_count={}, following_count={}, uid={}",
                vo.getFollowerCount(), vo.getFollowingCount(), vo.getUid());

        // 查看他人主页时：判断当前用户是否已关注对方
        if (loginUserId != null && queryUid != null && !queryUid.equals(loginUserId)) {
            boolean isFollowed = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, loginUserId)
                    .eq(Follow::getFollowId, queryUid)) > 0;
            vo.setIsFollowed(isFollowed);
        }

        return Result.ok(vo);
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
                long followerCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowId, queryUid));
                long followingCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, queryUid));
                user.setFollowerCount(followerCount);
                user.setFollowingCount(followingCount);
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
    public Result<Map<String, Object>> collect(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        var page = videoService.getCollectedVideos(userId, pageNo, pageSize);
        return Result.ok(Map.of(
                "video", Map.of("total", page.getTotal(), "list", page.getList()),
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

    /** 获取关注列表 */
    @GetMapping("/followings")
    public Result<List<UserVO>> followings(@RequestParam Long uid) {
        return Result.ok(userService.getFollowings(uid));
    }

    /** 获取粉丝列表 */
    @GetMapping("/followers")
    public Result<List<UserVO>> followers(@RequestParam Long uid) {
        return Result.ok(userService.getFollowers(uid));
    }

    /** 切换关注 */
    @PostMapping("/follow/{userId}")
    public Result<Map<String, Object>> toggleFollow(@PathVariable Long userId, HttpServletRequest req) {
        Long loginUserId = getLoginUserId(req);
        if (loginUserId == null) return Result.fail("请先登录");
        boolean followed = userService.toggleFollow(loginUserId, userId);

        // 关注通知 → Kafka / 直写 DB
        if (followed) {
            messagePublisher.publishNotification(new NotificationEvent(
                    userId, loginUserId, NotificationVO.TYPE_FOLLOW,
                    null, null, "关注了你", System.currentTimeMillis()));
        }

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
