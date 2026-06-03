package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.Comment;
import com.douyin.entity.Video;
import com.douyin.service.CommentService;
import com.douyin.service.CoverService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
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

    public VideoController(VideoService videoService, CommentService commentService,
                           CoverService coverService, JwtUtil jwtUtil) {
        this.videoService = videoService;
        this.commentService = commentService;
        this.coverService = coverService;
        this.jwtUtil = jwtUtil;
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

    /** 视频评论 */
    @GetMapping("/comments")
    public Result<List<Map<String, Object>>> comments(@RequestParam Long id) {
        return Result.ok(commentService.getVideoComments(id));
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
        return Result.ok(Map.of("isLoved", liked, "likeCount", video != null ? video.getLikeCount() : 0));
    }

    /** 记录分享 */
    @PostMapping("/share/{videoId}")
    public Result<Map<String, Object>> recordShare(@PathVariable Long videoId) {
        long count = videoService.recordShare(videoId);
        return Result.ok(Map.of("shareCount", count));
    }

    /** 发表评论 */
    @PostMapping("/comments")
    public Result<Comment> addComment(@RequestBody Comment comment, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        comment.setUserId(userId);
        return Result.ok(commentService.addComment(comment));
    }
}
