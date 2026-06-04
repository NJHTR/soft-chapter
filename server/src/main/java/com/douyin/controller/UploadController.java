package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.service.FileService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传到 MinIO, 配置 minio.enabled=true 后生效
 */
@RestController
@RequestMapping("/api/upload")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class UploadController {

    @Autowired
    private FileService fileService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    @PostMapping("/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        if (getLoginUserId(req) == null) {
            return Result.fail("请先登录");
        }
        try {
            String url = fileService.uploadVideo(file);
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        if (getLoginUserId(req) == null) {
            return Result.fail("请先登录");
        }
        try {
            String url = fileService.uploadImage(file);
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/voice")
    public Result<Map<String, Object>> uploadVoice(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        if (getLoginUserId(req) == null) {
            return Result.fail("请先登录");
        }
        try {
            String url = fileService.uploadVideo(file);  // 音频也用 video 桶
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            return Result.fail("上传失败: " + e.getMessage());
        }
    }
}
