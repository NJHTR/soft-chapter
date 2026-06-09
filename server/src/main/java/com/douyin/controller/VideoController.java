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
import com.douyin.service.ContentFeatureService;
import com.douyin.service.CoverService;
import com.douyin.service.MessageService;
import com.douyin.service.MusicService;
import com.douyin.service.SystemNoticeService;
import com.douyin.service.VideoMergeService;
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
    private final ContentFeatureService contentFeatureService;
    private final MusicService musicService;
    private final VideoMergeService videoMergeService;
    private final SystemNoticeService systemNoticeService;

    public VideoController(VideoService videoService, CommentService commentService,
                           CoverService coverService, JwtUtil jwtUtil,
                           MessagePublisher messagePublisher, MessageService messageService,
                           UserMapper userMapper, ContentFeatureService contentFeatureService,
                           MusicService musicService, VideoMergeService videoMergeService,
                           SystemNoticeService systemNoticeService) {
        this.videoService = videoService;
        this.commentService = commentService;
        this.coverService = coverService;
        this.jwtUtil = jwtUtil;
        this.messagePublisher = messagePublisher;
        this.messageService = messageService;
        this.userMapper = userMapper;
        this.contentFeatureService = contentFeatureService;
        this.musicService = musicService;
        this.videoMergeService = videoMergeService;
        this.systemNoticeService = systemNoticeService;
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
        return Result.ok(videoService.getRecommended(viewerUserId, start, pageSize, "recommend-video"));
    }

    /** 长视频推荐（时长 >= 60 秒） */
    @GetMapping("/long/recommended")
    public Result<PageDTO<VideoVO>> longRecommended(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getRecommended(viewerUserId, (pageNo - 1) * pageSize, pageSize, "long-video"));
    }

    /** 关注页：关注用户的视频 */
    @GetMapping("/following")
    public Result<PageDTO<VideoVO>> following(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        int pageNo = start / pageSize + 1;
        return Result.ok(videoService.getFollowingVideos(viewerUserId, pageNo, pageSize));
    }

    /** 热点：按热度排序 */
    @GetMapping("/trending")
    public Result<PageDTO<VideoVO>> trending(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        int pageNo = start / pageSize + 1;
        return Result.ok(videoService.getTrendingVideos(viewerUserId, pageNo, pageSize));
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
        if (c != null) {
            try {
                if (liked) contentFeatureService.onCommentLike(userId, commentId, c.getVideoId());
                else contentFeatureService.onCommentDislike(userId, commentId, c.getVideoId());
            } catch (Exception ignored) {}
        }
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

    /** 发布视频 (支持选配乐合成) */
    @PostMapping
    public Result<Video> publish(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        String videoUrl = (String) body.get("video_url");
        double duration = body.get("duration") != null ? Double.parseDouble(body.get("duration").toString()) : 15.0;
        String musicTitle = (String) body.getOrDefault("music_title", "原创");

        // 音乐合成 (可选)
        Long musicId = body.get("music_id") != null ? Long.valueOf(body.get("music_id").toString()) : null;
        double bgmVolume = body.get("bgm_volume") != null ? Double.parseDouble(body.get("bgm_volume").toString()) : 0.7;
        double bgmStartOffset = body.get("bgm_start_offset") != null
                ? Double.parseDouble(body.get("bgm_start_offset").toString()) : 0;
        double trimStart = body.get("trim_start") != null ? Double.parseDouble(body.get("trim_start").toString()) : 0;
        double trimEnd = body.get("trim_end") != null ? Double.parseDouble(body.get("trim_end").toString()) : 0;

        if (musicId != null) {
            try {
                com.douyin.entity.Music music = musicService.getById(musicId);
                if (music != null) {
                    String musicUrl = music.getPlayUrl();
                    musicTitle = music.getName() + " - " + music.getArtist();
                    if (musicUrl != null && !musicUrl.isEmpty()) {
                        log.info("publish: 开始合成BGM musicId={} name={} volume={} trim=[{},{}]",
                                musicId, music.getName(), bgmVolume,
                                String.format("%.1f", trimStart), String.format("%.1f", trimEnd));
                        String mergedPath = videoMergeService.merge(
                                videoUrl, musicUrl, music.getName(), duration, bgmVolume, bgmStartOffset,
                                trimStart, trimEnd);
                        videoUrl = mergedPath;
                        log.info("publish: BGM合成完成 → {}", videoUrl);
                    }
                }
            } catch (Exception e) {
                log.error("publish: BGM合成失败, 使用原始视频", e);
            }
        }

        // 无BGM但有裁剪: 仍需对原视频裁剪
        if (musicId == null && trimEnd > trimStart && trimEnd > 0) {
            try {
                log.info("publish: 无BGM裁剪 trim=[{},{}]", String.format("%.1f", trimStart), String.format("%.1f", trimEnd));
                String trimmedPath = videoMergeService.trimVideo(videoUrl, trimStart, trimEnd);
                videoUrl = trimmedPath;
            } catch (Exception e) {
                log.error("publish: 视频裁剪失败", e);
            }
        }

        // 更新实际发布时长
        if (trimEnd > trimStart && trimEnd > 0) {
            duration = trimEnd - trimStart;
        }

        // 编辑器分段信息 (含变速，后续版本做 ffmpeg setpts/atempo 处理)
        String segmentsJson = (String) body.get("segments");
        if (segmentsJson != null && !segmentsJson.isEmpty()) {
            log.info("publish: editor segments={}", segmentsJson);
        }

        Video video = new Video();
        video.setVideoUrl(videoUrl);
        video.setCoverUrl((String) body.getOrDefault("cover_url", ""));
        video.setDesc((String) body.getOrDefault("desc", ""));
        video.setDuration(duration);
        video.setWidth(1080);
        video.setHeight(1920);
        video.setAuthorUserId(userId);
        video.setMusicId(musicId);
        video.setMusicTitle(musicTitle);
        video.setBgmStartOffset(bgmStartOffset);
        video.setBgmVolume(bgmVolume);
        // 内容类型：视频/图片/文字
        String contentType = (String) body.getOrDefault("type", "recommend-video");
        video.setType(contentType);

        // 多图URL列表 (图文轮播)
        String imageUrls = (String) body.get("image_urls");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            video.setImageUrls(imageUrls);
            // 多图帖子的封面用第一张图
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<String> urls = mapper.readValue(imageUrls,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                if (!urls.isEmpty()) {
                    video.setCoverUrl(urls.get(0));
                }
            } catch (Exception ignored) {}
        }
        video.setStatus("PENDING");
        boolean saved = videoService.save(video);
        log.info("publish: save result={}, videoId={}", saved, video.getId());

        // 视频: ffmpeg 抽取封面帧; 图文/文字: 已有封面或第一张图
        if (!"image".equals(contentType) && !"text".equals(contentType)) {
            try {
                String coverUrl = coverService.extractAndUpload(video.getVideoUrl());
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    video.setCoverUrl(coverUrl);
                    videoService.updateById(video);
                }
            } catch (Exception e) {
                log.error("publish: cover extraction failed", e);
            }
        }

        // 异步提取内容特征 (视频/图文/文字均支持)
        contentFeatureService.extractAsync(video);

        // 发布成功系统通知
        try {
            String typeLabel = "image".equals(contentType) ? "图片" : "text".equals(contentType) ? "文字" : "视频";
            String desc = video.getDesc();
            if (desc == null || desc.isEmpty()) desc = "无描述";
            String timeStr = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            systemNoticeService.send(userId, "publish_" + contentType,
                    typeLabel + "作品发布成功",
                    "您的" + typeLabel + "作品「" + desc + "」已于 " + timeStr + " 发布成功。可以在个人主页查看。");
        } catch (Exception ignored) {}

        return Result.ok(video);
    }

    /** 接收 Python 特征提取流水线结果 */
    @PostMapping("/content-features")
    public Result<?> receiveContentFeatures(@RequestBody Map<String, Object> features) {
        log.info("收到内容特征: videoId={}", features.get("video_id"));
        contentFeatureService.saveFeatures(features);
        return Result.ok();
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

        // 更新用户画像
        if (liked && video != null) {
            try { contentFeatureService.onLike(userId, videoId, video.getAuthorUserId()); } catch (Exception e) { log.warn("画像更新失败", e); }
        }

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
    public Result<Map<String, Object>> recordShare(@PathVariable Long videoId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        long count = videoService.recordShare(videoId);
        if (userId != null) {
            Video video = videoService.getById(videoId);
            try { contentFeatureService.onShare(userId, videoId, video != null ? video.getAuthorUserId() : null); } catch (Exception ignored) {}
        }
        return Result.ok(Map.of("shareCount", count));
    }

    /** 切换收藏 */
    @PostMapping("/collect/{videoId}")
    public Result<Map<String, Object>> toggleCollect(@PathVariable Long videoId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        boolean collected = videoService.toggleCollect(userId, videoId);
        Video video = videoService.getById(videoId);

        // 更新用户画像
        if (collected && video != null) {
            try { contentFeatureService.onCollect(userId, videoId, video.getAuthorUserId()); } catch (Exception e) { log.warn("画像更新失败", e); }
        }

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

        // 更新用户画像: 评论行为
        if (video != null) {
            try { contentFeatureService.onComment(userId, comment.getVideoId(), video.getAuthorUserId()); } catch (Exception ignored) {}
        }
        log.info("[NOTIF] video found: id={} authorUserId={}", video != null ? video.getId() : null,
                video != null ? video.getAuthorUserId() : null);

        // 评论通知 → 视频作者
        if (video != null && !userId.equals(video.getAuthorUserId())) {
            log.info("[NOTIF] comment notify: author={} commenter={}", video.getAuthorUserId(), userId);
            pubNotif(video.getAuthorUserId(), userId, NotificationVO.TYPE_COMMENT,
                    comment.getVideoId(), saved.getId(),
                    buildNotifContent(comment));
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
                        comment.getVideoId(), saved.getId(), buildReplyNotifContent(comment));
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
                    String atContent = buildNotifContent(comment);
                    pubNotif(mentionedUser.getUid(), userId, NotificationVO.TYPE_AT,
                            comment.getVideoId(), saved.getId(),
                            atContent.equals("评论了你的作品") ? "在评论中@了你" : atContent);
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
        // 异步发布到 Kafka，避免阻塞 HTTP 请求线程
        final Long fUserId = userId, fFromUserId = fromUserId, fVideoId = videoId, fCommentId = commentId;
        final Integer fType = type;
        final String fContent = content;
        final MessagePublisher pub = messagePublisher;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                pub.publishNotification(new NotificationEvent(
                        fUserId, fFromUserId, fType, fVideoId, fCommentId, fContent, System.currentTimeMillis()));
                log.info("[NOTIF] Kafka published: toUser={} type={}", fUserId, fType);
            } catch (Exception e) {
                log.error("[NOTIF] Kafka publish failed: toUser={} type={} error={}", fUserId, fType, e.getMessage(), e);
            }
        });
    }

    /** 根据评论内容生成通知文案：文本优先，纯媒体则显示类型标签 */
    private String buildNotifContent(Comment comment) {
        String text = comment.getContent();
        if (text != null && !text.isBlank()) return text;
        String mediaLabel = getMediaLabel(comment.getExtra());
        return !mediaLabel.isEmpty() ? mediaLabel : "评论了你的作品";
    }

    private String buildReplyNotifContent(Comment comment) {
        String text = comment.getContent();
        if (text != null && !text.isBlank()) return text;
        String mediaLabel = getMediaLabel(comment.getExtra());
        return !mediaLabel.isEmpty() ? mediaLabel : "回复了你的评论";
    }

    private String getMediaLabel(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) return "";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> mediaList = mapper.readValue(
                    extraJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            boolean hasImage = mediaList.stream().anyMatch(m -> "image".equals(m.get("type")));
            boolean hasVoice = mediaList.stream().anyMatch(m -> "voice".equals(m.get("type")));
            if (hasImage && hasVoice) return "[图片+语音]";
            if (hasImage) return "[图片]";
            if (hasVoice) return "[语音]";
        } catch (Exception ignored) {}
        return "";
    }

    /** 记录观看历史 + 更新用户画像 */
    @PostMapping("/watch/{videoId}")
    public Result<?> recordWatch(@PathVariable Long videoId,
                                 @RequestBody Map<String, Object> body,
                                 HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Video video = videoService.getById(videoId);
        if (video == null) return Result.fail("视频不存在");
        double watchDuration = body.get("watch_duration") != null
                ? Double.parseDouble(body.get("watch_duration").toString()) : 0;
        double videoDuration = body.get("video_duration") != null
                ? Double.parseDouble(body.get("video_duration").toString()) : 0;
        boolean finished = body.get("finished") != null && Boolean.parseBoolean(body.get("finished").toString());
        String trafficSource = body.get("traffic_source") != null
                ? body.get("traffic_source").toString() : "HOME_RECOMMEND";
        String sessionId = body.get("session_id") != null
                ? body.get("session_id").toString() : null;
        double swipeSeconds = body.get("swipe_seconds") != null
                ? Double.parseDouble(body.get("swipe_seconds").toString()) : watchDuration;

        videoService.recordWatch(userId, videoId, video.getAuthorUserId(),
                watchDuration, videoDuration, finished,
                trafficSource, sessionId, swipeSeconds);

        // 更新全行为画像
        try {
            contentFeatureService.onWatch(userId, videoId, video.getAuthorUserId(),
                    watchDuration, videoDuration, trafficSource, sessionId, swipeSeconds);
        } catch (Exception e) {
            log.warn("画像更新失败: userId={} videoId={}", userId, videoId, e);
        }

        return Result.ok();
    }

    /** 搜索视频 */
    @GetMapping("/search")
    public Result<List<VideoVO>> search(@RequestParam String keyword) {
        return Result.ok(videoService.searchVideos(keyword));
    }
}
