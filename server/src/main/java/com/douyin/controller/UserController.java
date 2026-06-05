package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.dto.UpdateProfileDTO;
import com.douyin.entity.Follow;
import com.douyin.entity.User;
import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.entity.Friend;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.FriendMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.SystemNoticeService;
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
    private final FriendMapper friendMapper;
    private final MessagePublisher messagePublisher;
    private final ContentFeatureService contentFeatureService;
    private final SystemNoticeService systemNoticeService;

    public UserController(UserService userService, VideoService videoService, JwtUtil jwtUtil,
                          FollowMapper followMapper, FriendMapper friendMapper,
                          MessagePublisher messagePublisher,
                          ContentFeatureService contentFeatureService,
                          SystemNoticeService systemNoticeService) {
        this.userService = userService;
        this.videoService = videoService;
        this.jwtUtil = jwtUtil;
        this.followMapper = followMapper;
        this.friendMapper = friendMapper;
        this.messagePublisher = messagePublisher;
        this.contentFeatureService = contentFeatureService;
        this.systemNoticeService = systemNoticeService;
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

        // 查看他人主页时：判断当前用户是否已关注对方 & 对方是否关注我
        if (loginUserId != null && queryUid != null && !queryUid.equals(loginUserId)) {
            boolean isFollowed = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, loginUserId)
                    .eq(Follow::getFollowId, queryUid)) > 0;
            vo.setIsFollowed(isFollowed);
            boolean isFollowingMe = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, queryUid)
                    .eq(Follow::getFollowId, loginUserId)) > 0;
            vo.setIsFollowingMe(isFollowingMe);

            // 好友状态: 是否为确认的朋友
            boolean isFriend = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                    .and(w -> w.and(a -> a.eq(Friend::getUserId, loginUserId).eq(Friend::getFriendId, queryUid))
                            .or(a -> a.eq(Friend::getUserId, queryUid).eq(Friend::getFriendId, loginUserId)))
                    .eq(Friend::getStatus, 1)) > 0;
            vo.setIsFriend(isFriend);

            // 是否已发送好友申请(待处理)
            boolean friendRequestSent = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                    .eq(Friend::getUserId, loginUserId)
                    .eq(Friend::getFriendId, queryUid)
                    .eq(Friend::getStatus, 0)) > 0;
            vo.setFriendRequestSent(friendRequestSent);
        }

        // 互关数 & 朋友数
        if (queryUid != null) {
            // 互关: 双方互相关注
            long mutualCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, queryUid)
                    .inSql(Follow::getFollowId,
                            "SELECT user_id FROM t_follow WHERE follow_id = " + queryUid));
            vo.setMutualCount(mutualCount);

            // 朋友: 双向确认 (t_friend status=1)
            long friendCount = friendMapper.selectCount(new LambdaQueryWrapper<com.douyin.entity.Friend>()
                    .and(w -> w.eq(com.douyin.entity.Friend::getUserId, queryUid)
                            .or().eq(com.douyin.entity.Friend::getFriendId, queryUid))
                    .eq(com.douyin.entity.Friend::getStatus, 1));
            vo.setFriendCount(friendCount);
        }

        return Result.ok(vo);
    }

    /** 好友列表 */
    @GetMapping("/friends")
    public Result<Object> friends() {
        return Result.ok(userService.getFriends());
    }

    /** 朋友列表 (互相关注) */
    @GetMapping("/friends-mutual")
    public Result<List<UserVO>> friendsMutual(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(userService.getFriends(userId));
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
                UserVO vo = UserVO.from(user);

                // 互关数
                long mutualCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, queryUid)
                        .inSql(Follow::getFollowId,
                                "SELECT user_id FROM t_follow WHERE follow_id = " + queryUid));
                vo.setMutualCount(mutualCount);

                // 朋友数 (双向确认)
                long friendCount = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                        .and(w -> w.eq(Friend::getUserId, queryUid)
                                .or().eq(Friend::getFriendId, queryUid))
                        .eq(Friend::getStatus, 1));
                vo.setFriendCount(friendCount);

                // 查看他人时判断双方关注关系
                if (loginUserId != null && !queryUid.equals(loginUserId)) {
                    boolean isFollowed = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, loginUserId)
                            .eq(Follow::getFollowId, queryUid)) > 0;
                    vo.setIsFollowed(isFollowed);
                    boolean isFollowingMe = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, queryUid)
                            .eq(Follow::getFollowId, loginUserId)) > 0;
                    vo.setIsFollowingMe(isFollowingMe);
                }

                return Result.ok(vo);
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

        // 更新用户画像
        try { contentFeatureService.onFollow(loginUserId, userId); } catch (Exception ignored) {}

        // 关注通知 → 异步发 Kafka，避免阻塞 HTTP 请求
        if (followed) {
            final Long fUserId = userId, fLoginUserId = loginUserId;
            final com.douyin.kafka.MessagePublisher pub = messagePublisher;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    pub.publishNotification(new NotificationEvent(
                            fUserId, fLoginUserId, NotificationVO.TYPE_FOLLOW,
                            null, null, "关注了你", System.currentTimeMillis()));
                } catch (Exception ignored) {}
            });
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

    /** 搜索用户 */
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword) {
        return Result.ok(userService.searchUsers(keyword));
    }

    /** 最近常看：根据互动历史获取最近关注的作者 */
    @GetMapping("/recent-authors")
    public Result<List<UserVO>> recentAuthors(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(userService.getRecentAuthors(userId, 6));
    }

    /** 记录访客 */
    @PostMapping("/visitors/{userId}")
    public Result<?> recordVisit(@PathVariable Long userId, HttpServletRequest req) {
        Long visitorId = getLoginUserId(req);
        if (visitorId == null) return Result.fail("请先登录");
        userService.recordVisit(userId, visitorId);
        try { contentFeatureService.onProfileVisit(visitorId, userId); } catch (Exception ignored) {}
        return Result.ok();
    }

    /** 获取我的访客 */
    @GetMapping("/visitors")
    public Result<List<UserVO>> visitors(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(userService.getVisitors(userId, 20));
    }

    /** 设置主页访客展示开关 */
    @PutMapping("/visitor-display")
    public Result<?> setVisitorDisplay(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        userService.setVisitorDisplay(userId, enabled);
        return Result.ok();
    }

    // ============ 密码管理 ============

    /** 查询是否已设置密码 */
    @GetMapping("/has-password")
    public Result<Boolean> hasPassword(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        return Result.ok(userService.hasPassword(userId));
    }

    /** 设置或修改密码 */
    @PutMapping("/password")
    public Result<?> setPassword(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return Result.fail("密码至少6位");
        }
        try {
            userService.setPassword(userId, password);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    // ============ 朋友系统 ============

    /** 发送朋友申请 (双方必须互关) */
    @PostMapping("/friend/request")
    public Result<?> sendFriendRequest(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long targetId = Long.valueOf(body.get("target_id").toString());

        if (!friendMapper.isMutualFollow(userId, targetId)) {
            return Result.fail("双方需要互相关注才能添加朋友");
        }

        // 检查是否已经是朋友
        Long existCount = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .and(w -> w.and(a -> a.eq(Friend::getUserId, userId).eq(Friend::getFriendId, targetId))
                        .or(a -> a.eq(Friend::getUserId, targetId).eq(Friend::getFriendId, userId)))
                .eq(Friend::getStatus, 1));
        if (existCount > 0) return Result.fail("已经是朋友了");

        // 检查是否已有待处理申请
        Long pendingCount = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .and(w -> w.and(a -> a.eq(Friend::getUserId, userId).eq(Friend::getFriendId, targetId))
                        .or(a -> a.eq(Friend::getUserId, targetId).eq(Friend::getFriendId, userId)))
                .eq(Friend::getStatus, 0));
        if (pendingCount > 0) return Result.fail("已有待处理的申请");

        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setFriendId(targetId);
        friend.setStatus(0);
        friendMapper.insert(friend);

        // 推送通知
        try {
            messagePublisher.publishNotification(new NotificationEvent(
                    targetId, userId, NotificationVO.TYPE_FRIEND_REQUEST,
                    null, null, "请求添加你为朋友", System.currentTimeMillis()));
        } catch (Exception ignored) {}

        return Result.ok();
    }

    /** 接受朋友申请 */
    @PostMapping("/friend/accept")
    public Result<?> acceptFriendRequest(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long fromId = Long.valueOf(body.get("from_id").toString());

        Friend friend = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, fromId)
                .eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 0));
        if (friend == null) return Result.fail("未找到该申请");

        friend.setStatus(1);
        friendMapper.updateById(friend);

        try {
            messagePublisher.publishNotification(new NotificationEvent(
                    fromId, userId, NotificationVO.TYPE_FRIEND_ACCEPTED,
                    null, null, "接受了你的朋友申请", System.currentTimeMillis()));
        } catch (Exception ignored) {}

        // 好友申请通过系统通知 → 通知发起方
        try {
            User acceptor = userService.getById(userId);
            User requester = userService.getById(fromId);
            String timeStr = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String acceptorName = acceptor != null ? acceptor.getNickname() : "用户";
            String requesterName = requester != null ? requester.getNickname() : "用户";
            systemNoticeService.send(fromId, "friend_accepted",
                    "好友申请已通过",
                    requesterName + "，您好！" + acceptorName + " 已于 " + timeStr
                            + " 通过了您的好友申请。你们现在可以开始聊天了。");
            systemNoticeService.send(userId, "friend_accepted",
                    "好友添加成功",
                    acceptorName + "，您好！您已成功添加 " + requesterName + " 为好友（" + timeStr + "）。");
        } catch (Exception ignored) {}

        return Result.ok();
    }

    /** 拒绝朋友申请 */
    @PostMapping("/friend/reject")
    public Result<?> rejectFriendRequest(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long fromId = Long.valueOf(body.get("from_id").toString());

        Friend friend = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, fromId)
                .eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 0));
        if (friend == null) return Result.fail("未找到该申请");

        friend.setStatus(2);
        friendMapper.updateById(friend);
        return Result.ok();
    }

    /** 朋友列表 */
    @GetMapping("/friend/list")
    public Result<List<UserVO>> friendList(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        List<User> friends = friendMapper.findFriends(userId);
        return Result.ok(friends.stream().map(u -> {
            UserVO vo = UserVO.from(u);
            vo.setIsFollowed(true);
            vo.setIsFollowingMe(true);
            return vo;
        }).toList());
    }

    /** 待处理的朋友申请 */
    @GetMapping("/friend/requests")
    public Result<List<UserVO>> friendRequests(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        List<Friend> requests = friendMapper.findPendingRequests(userId);
        List<Long> fromIds = requests.stream().map(Friend::getUserId).toList();
        if (fromIds.isEmpty()) return Result.ok(List.of());
        List<User> users = userService.listByIds(fromIds);
        return Result.ok(users.stream().map(UserVO::from).toList());
    }
}
