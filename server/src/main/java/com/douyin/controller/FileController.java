package com.douyin.controller;

import com.douyin.service.FileService;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件访问 — 从 MinIO 流式返回文件内容，支持 HTTP Range 分段请求。
 * <p>
 * 视频文件支持 Range 后，浏览器可分段加载、拖拽进度条任意位置即时播放，
 * 弱网断点续传。图片文件直接全量返回。
 */
@RestController
@RequestMapping("/api/file")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class FileController {

    @Autowired
    private FileService fileService;

    @GetMapping("/url")
    public void getUrl(@RequestParam String path,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        String[] parts = extractKey(path).split("/", 2);
        if (parts.length != 2) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"msg\":\"Invalid path\",\"data\":null}");
            return;
        }

        String bucket = parts[0];
        String objectName = parts[1];
        String contentType = getContentType(objectName);
        boolean isVideo = contentType.startsWith("video/") || contentType.startsWith("audio/");

        try {
            StatObjectResponse stat = fileService.statObject(bucket, objectName);
            long fileSize = stat.size();
            String rangeHeader = request.getHeader("Range");

            if (isVideo && rangeHeader != null && fileSize > 0) {
                serveRange(response, bucket, objectName, rangeHeader, fileSize, contentType);
            } else {
                serveFull(response, bucket, objectName, fileSize, contentType);
            }
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.setStatus(404);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":404,\"msg\":\"File not found\",\"data\":null}");
            }
        }
    }

    /** 全量返回（图片或不支持 Range 的情况） */
    private void serveFull(HttpServletResponse response, String bucket, String objectName,
                           long fileSize, String contentType) throws Exception {
        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");
        if (fileSize > 0) response.setContentLengthLong(fileSize);
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");

        try (InputStream in = fileService.getObject(bucket, objectName);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /** HTTP 206 分段返回 */
    private void serveRange(HttpServletResponse response, String bucket, String objectName,
                            String rangeHeader, long fileSize, String contentType) throws Exception {
        // 解析 Range: bytes=start-end (start 和 end 均为 inclusive)
        long start;
        long end;

        if (!rangeHeader.startsWith("bytes=")) {
            serveFull(response, bucket, objectName, fileSize, contentType);
            return;
        }

        String rangeValue = rangeHeader.substring(6);
        int dashIdx = rangeValue.indexOf('-');
        if (dashIdx < 0) {
            serveFull(response, bucket, objectName, fileSize, contentType);
            return;
        }

        try {
            String startStr = rangeValue.substring(0, dashIdx).trim();
            String endStr = rangeValue.substring(dashIdx + 1).trim();

            if (startStr.isEmpty()) {
                // bytes=-suffix → 最后 suffix 字节
                long suffix = Long.parseLong(endStr);
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startStr);
                if (endStr.isEmpty()) {
                    end = fileSize - 1;
                } else {
                    end = Math.min(Long.parseLong(endStr), fileSize - 1);
                }
            }
        } catch (NumberFormatException e) {
            serveFull(response, bucket, objectName, fileSize, contentType);
            return;
        }

        // 校验范围合法性
        if (start < 0) start = 0;
        if (end >= fileSize) end = fileSize - 1;
        if (start > end) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + fileSize);
            return;
        }

        long contentLength = end - start + 1;

        response.setStatus(206);
        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        response.setContentLengthLong(contentLength);
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");

        try (InputStream in = fileService.getObject(bucket, objectName, start, contentLength);
             OutputStream out = response.getOutputStream()) {

            byte[] buf = new byte[8192];
            int bytesRead;
            long remaining = contentLength;
            while (remaining > 0 && (bytesRead = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }

    private String extractKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("path is required");
        }
        if (!raw.contains("://")) {
            return raw;
        }
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
