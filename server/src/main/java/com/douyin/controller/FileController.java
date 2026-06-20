package com.douyin.controller;

import com.douyin.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件访问 — 从 MinIO 流式返回文件内容，无过期时间限制。
 * <p>
 * 数据库只存对象路径 (如 {@code douyin-image/uuid.jpg})，
 * 前端 {@code _checkImgUrl} 将对象路径转为 {@code /api/file/url?path=xxx}，
 * 本接口直接返回文件二进制内容。
 */
@RestController
@RequestMapping("/api/file")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 根据对象路径返回文件内容。
     * <p>
     * {@code <img src="/api/file/url?path=douyin-image/uuid.jpg">}
     * → 直接输出图片二进制 (Content-Type 根据扩展名推断)
     *
     * @param path 对象路径，格式: {@code bucket/objectName}
     */
    @GetMapping("/url")
    public void getUrl(@RequestParam String path, HttpServletResponse response) throws IOException {
        String[] parts = extractKey(path).split("/", 2);
        if (parts.length != 2) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"msg\":\"Invalid path\",\"data\":null}");
            return;
        }

        String bucket = parts[0];
        String objectName = parts[1];

        response.setContentType(getContentType(objectName));
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");

        try (InputStream in = fileService.getObject(bucket, objectName);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.setStatus(404);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":404,\"msg\":\"File not found\",\"data\":null}");
            }
        }
    }

    private String extractKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("path is required");
        }
        // 新格式: bucket/objectName
        if (!raw.contains("://")) {
            return raw;
        }
        // 旧预签名 URL: 提取路径
        try {
            java.net.URI uri = new java.net.URI(raw);
            String uriPath = uri.getPath();
            if (uriPath.startsWith("/")) uriPath = uriPath.substring(1);
            return uriPath;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + raw);
        }
    }

    private String getContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }
}
