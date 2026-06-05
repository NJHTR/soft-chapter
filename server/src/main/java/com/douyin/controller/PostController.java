package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.VideoVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post")
public class PostController {

    private final VideoService videoService;
    private final JwtUtil jwtUtil;

    public PostController(VideoService videoService, JwtUtil jwtUtil) {
        this.videoService = videoService;
        this.jwtUtil = jwtUtil;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try { return jwtUtil.getUserIdFromToken(token); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 图文推荐 */
    @GetMapping("/recommended")
    public Result<PageDTO<VideoVO>> recommended(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        Long viewerUserId = getLoginUserId(req);
        return Result.ok(videoService.getRecommendedPosts(viewerUserId, pageNo, pageSize));
    }
}
