package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.Comment;
import com.douyin.entity.Notification;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.mapper.UserMapper;
import com.douyin.service.CommentService;
import com.douyin.service.CoverService;
import com.douyin.service.MessageService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.NotificationVO;
import com.douyin.vo.VideoVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoService videoService;
    private final CommentService commentService;
    private final CoverService coverService;
    private final JwtUtil jwtUtil;
    private final MessagePublisher messagePublisher;
    private final MessageService messageService;
    private final UserMapper userMapper;

    public VideoController(VideoService videoService, CommentService commentService,
                           CoverService coverService, JwtUtil jwtUtil,
                           MessagePublisher messagePublisher, MessageService messageService,
                           UserMapper userMapper) {
        this.videoService = videoService;
        this.commentService = commentService;
        this.coverService = coverService;
        this.jwtUtil = jwtUtil;
        this.messagePublisher = messagePublisher;
        this.messageService = messageService;
        this.userMapper = userMapper;
    }

    /** 从请求头提取当前登录用户ID, 未登录返回 null */
    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 推荐视频 */
    @GetMapping("/recommended")
    public Result<PageDTO<VideoVO>> recommended(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "6") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getRecommended(viewerUserId, start, pageSize));
    }

    /** 长视频推荐 */
    @GetMapping("/long/recommended")
    public Result<PageDTO<VideoVO>> longRecommended(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getRecommended(viewerUserId, (pageNo - 1) * pageSize, pageSize));
    }

    /** 视频评论（分页） */
    @GetMapping("/comments")
    public Result<List<Map<String, Object>>> comments(
            @RequestParam Long id,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "15") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(commentService.getVideoComments(id, viewerUserId, pageNo, pageSize));
    }

    /** 点赞/取消点赞评论 */
    @PostMapping("/comment/like/{commentId}")
    public Result<Map<String, Object>> toggleCommentLike(@PathVariable Long commentId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        boolean liked = commentService.toggleCommentLike(userId, commentId);
        Comment c = commentService.getById(commentId);
        return Result.ok(Map.of("isLoved", liked, "likeCount", c != null ? c.getLikeCount() : 0));
    }

    /** 获取子评论 */
    @GetMapping("/comment/replies/{commentId}")
    public Result<List<Map<String, Object>>> getReplies(@PathVariable Long commentId, HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(commentService.getCommentReplies(commentId, viewerUserId));
    }

    /** 我的视频 */
    @GetMapping("/my")
    public Result<PageDTO<VideoVO>> my(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(videoService.getMyVideos(userId, userId, pageNo, pageSize));
    }

    /** 点赞视频 */
    @GetMapping("/like")
    public Result<PageDTO<VideoVO>> like(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(videoService.getLikedVideos(userId, pageNo, pageSize));
    }

    /** 私密视频 */
    @GetMapping("/private")
    public Result<PageDTO<VideoVO>> privateVideos(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getPrivateVideos(viewerUserId, pageNo, pageSize));
    }

    /** 观看历史 */
    @GetMapping("/history")
    public Result<PageDTO<VideoVO>> history(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getHistory(viewerUserId, pageNo, pageSize));
    }

    /** 其他浏览历史(图文/商品) */
    @GetMapping("/historyOther")
    public Result<PageDTO<VideoVO>> historyOther(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(videoService.getHistoryOther(pageNo, pageSize));
    }

    /** 发布视频 */
    @PostMapping
    public Result<Video> publish(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        Video video = new Video();
        video.setVideoUrl((String) body.get("video_url"));
        video.setCoverUrl((String) body.getOrDefault("cover_url", ""));
        video.setDesc((String) body.getOrDefault("desc", ""));
        video.setDuration(body.get("duration") != null ? Double.valueOf(body.get("duration").toString()) : 15.0);
        video.setWidth(1080);
        video.setHeight(1920);
        video.setAuthorUserId(userId);
        video.setMusicTitle((String) body.getOrDefault("music_title", "原创"));
        video.setType("recommend-video");
        boolean saved = videoService.save(video);
        log.info("publish: save result={}, videoId={}", saved, video.getId());

        // 保存后立即查询确认
        Video verify = videoService.getById(video.getId());
        log.info("publish: verify video exists={}", verify != null);

        try {
            String coverUrl = coverService.extractAndUpload(video.getVideoUrl());
            if (coverUrl != null && !coverUrl.isEmpty()) {
                video.setCoverUrl(coverUrl);
                videoService.updateById(video);
            }
        } catch (Exception e) {
            log.error("publish: cover extraction failed", e);
        }

        return Result.ok(video);
    }

    /** 切换点赞 */
    @PostMapping("/like/{videoId}")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long videoId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        log.info("toggleLike: userId={}, videoId={}", userId, videoId);
        boolean liked = videoService.toggleLike(userId, videoId);
        Video video = videoService.getById(videoId);
        log.info("toggleLike response: liked={}, likeCount={}, videoExists={}", liked,
                video != null ? video.getLikeCount() : null, video != null);

        // 点赞通知
        if (liked && video != null && !userId.equals(video.getAuthorUserId())) {
            log.info("[NOTIF] like notify: author={} liker={}", video.getAuthorUserId(), userId);
            pubNotif(video.getAuthorUserId(), userId, NotificationVO.TYPE_LIKE,
                    videoId, null, "赞了你的作品");
        }

        return Result.ok(Map.of("isLoved", liked, "likeCount", video != null ? video.getLikeCount() : 0));
    }

    /** 记录分享 */
    @PostMapping("/share/{videoId}")
    public Result<Map<String, Object>> recordShare(@PathVariable Long videoId) {
        long count = videoService.recordShare(videoId);
        return Result.ok(Map.of("shareCount", count));
    }

    /** 切换收藏 */
    @PostMapping("/collect/{videoId}")
    public Result<Map<String, Object>> toggleCollect(@PathVariable Long videoId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        boolean collected = videoService.toggleCollect(userId, videoId);
        Video video = videoService.getById(videoId);

        // 收藏通知
        if (collected && video != null && !userId.equals(video.getAuthorUserId())) {
            log.info("[NOTIF] collect notify: author={} collector={}", video.getAuthorUserId(), userId);
            pubNotif(video.getAuthorUserId(), userId, NotificationVO.TYPE_COLLECT,
                    videoId, null, "收藏了你的作品");
        }

        return Result.ok(Map.of("isCollected", collected, "collectCount",
                video != null ? video.getCollectCount() : 0));
    }

    /** 发表评论 */
    @PostMapping("/comments")
    public Result<Comment> addComment(@RequestBody Comment comment, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        log.info("[NOTIF] addComment: userId={} videoId={} parentId={} content={}",
                userId, comment.getVideoId(), comment.getParentId(),
                comment.getContent() != null ? comment.getContent().substring(0, Math.min(comment.getContent().length(), 50)) : null);
        comment.setUserId(userId);
        Comment saved = commentService.addComment(comment);
        Video video = videoService.getById(comment.getVideoId());
        log.info("[NOTIF] video found: id={} authorUserId={}", video != null ? video.getId() : null,
                video != null ? video.getAuthorUserId() : null);

        // 评论通知 → 视频作者
        if (video != null && !userId.equals(video.getAuthorUserId())) {
            log.info("[NOTIF] comment notify: author={} commenter={}", video.getAuthorUserId(), userId);
            pubNotif(video.getAuthorUserId(), userId, NotificationVO.TYPE_COMMENT,
                    comment.getVideoId(), saved.getId(),
                    comment.getContent() != null ? comment.getContent() : "评论了你的作品");
        } else {
            log.info("[NOTIF] skip comment self-notify: videoAuthor={} commenter={}",
                    video != null ? video.getAuthorUserId() : null, userId);
        }

        // 回复通知 → 被回复的评论作者（如果是回复，且不是回复自己或视频作者）
        if (comment.getParentId() != null && comment.getParentId() != 0) {
            Comment parentComment = commentService.getById(comment.getParentId());
            log.info("[NOTIF] reply check: parentId={} parentExists={} parentAuthor={}",
                    comment.getParentId(), parentComment != null,
                    parentComment != null ? parentComment.getUserId() : null);
            if (parentComment != null
                    && !parentComment.getUserId().equals(userId)
                    && (video == null || !parentComment.getUserId().equals(video.getAuthorUserId()))) {
                log.info("[NOTIF] reply notify: to={} from={}", parentComment.getUserId(), userId);
                pubNotif(parentComment.getUserId(), userId, NotificationVO.TYPE_COMMENT,
                        comment.getVideoId(), saved.getId(), "回复了你的评论");
            }
        }

        // @提及通知（支持两种格式: @[uid:name] 精确指定 / @name 昵称匹配）
        String content = comment.getContent();
        if (content != null && content.contains("@")) {
            java.util.regex.Pattern mentionPattern = java.util.regex.Pattern
                    .compile("@(?:\\[([^\\]:]+):([^\\]]+)\\]|([^@\\s]+))");
            java.util.regex.Matcher matcher = mentionPattern.matcher(content);
            while (matcher.find()) {
                String explicitId = matcher.group(1);
                String displayName = matcher.group(2);
                String nickname = matcher.group(3);

                User mentionedUser = null;
                if (explicitId != null) {
                    try {
                        mentionedUser = userMapper.selectById(Long.parseLong(explicitId));
                    } catch (NumberFormatException e) {
                        mentionedUser = userMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                        .eq(User::getNickname, displayName != null ? displayName : explicitId));
                    }
                } else if (nickname != null) {
                    mentionedUser = userMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                    .eq(User::getNickname, nickname));
                }

                if (mentionedUser != null
                        && !mentionedUser.getUid().equals(userId)
                        && (video == null || !mentionedUser.getUid().equals(video.getAuthorUserId()))) {
                    log.info("[NOTIF] at notify: to={} from={}", mentionedUser.getUid(), userId);
                    pubNotif(mentionedUser.getUid(), userId, NotificationVO.TYPE_AT,
                            comment.getVideoId(), saved.getId(), "在评论中@了你");
                }
            }
        }

        return Result.ok(saved);
    }

    /** 直接落库通知 + 发布到 Kafka（用于 WebSocket 实时推送） */
    private void pubNotif(Long userId, Long fromUserId, Integer type,
                          Long videoId, Long commentId, String content) {
        log.info("[NOTIF] pubNotif: toUser={} fromUser={} type={} videoId={} commentId={} content={}",
                userId, fromUserId, type, videoId, commentId, content);
        Notification n = new Notification();
        n.setUserId(userId);
        n.setFromUserId(fromUserId);
        n.setType(type);
        n.setVideoId(videoId);
        n.setCommentId(commentId);
        n.setContent(content);
        try {
            Notification saved = messageService.createNotification(n);
            log.info("[NOTIF] DB saved: id={} userId={} type={}", saved != null ? saved.getId() : "FAILED", userId, type);
        } catch (Exception e) {
            log.error("[NOTIF] DB save failed: userId={} type={} error={}", userId, type, e.getMessage(), e);
        }
        // 同步发布到 Kafka，消费者负责 WebSocket 推送
        try {
            messagePublisher.publishNotification(new NotificationEvent(
                    userId, fromUserId, type, videoId, commentId, content, System.currentTimeMillis()));
            log.info("[NOTIF] Kafka published: toUser={} type={}", userId, type);
        } catch (Exception e) {
            log.error("[NOTIF] Kafka publish failed: toUser={} type={} error={}", userId, type, e.getMessage(), e);
        }
    }

    /** 搜索视频 */
    @GetMapping("/search")
    public Result<List<VideoVO>> search(@RequestParam String keyword) {
        return Result.ok(videoService.searchVideos(keyword));
    }
}
