package com.douyin.service;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 视频封面提取 — 使用 FFmpeg 抓取第一帧
 */
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class CoverService {

    private static final Logger log = LoggerFactory.getLogger(CoverService.class);

    @org.springframework.beans.factory.annotation.Value("${server.port:9191}")
    private int serverPort;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private com.douyin.config.MinioConfig minioConfig;

    /**
     * 查找 ffmpeg 可执行文件路径，避免依赖 PATH 环境变量。
     */
    private String resolveFfmpeg() {
        // Windows winget 安装路径
        String userHome = System.getProperty("user.home");
        String wingetBase = userHome + "\\AppData\\Local\\Microsoft\\WinGet\\Packages";
        java.io.File wingetDir = new java.io.File(wingetBase);
        if (wingetDir.exists() && wingetDir.isDirectory()) {
            java.io.File[] dirs = wingetDir.listFiles(
                    (d, n) -> n.startsWith("Gyan.FFmpeg"));
            if (dirs != null) {
                for (java.io.File dir : dirs) {
                    java.io.File[] subs = dir.listFiles(
                            (d, n) -> n.startsWith("ffmpeg-"));
                    if (subs != null) {
                        for (java.io.File sub : subs) {
                            java.io.File exe = new java.io.File(sub, "bin\\ffmpeg.exe");
                            if (exe.exists()) return exe.getAbsolutePath();
                        }
                    }
                }
            }
        }
        // 兜底: 依赖 PATH
        return "ffmpeg";
    }

    /**
     * 从视频 URL 提取封面，上传到 MinIO，返回封面 URL。
     * 失败返回 null（不抛异常，不阻塞发布流程）。
     */
    public String extractAndUpload(String videoUrl) {
        Path tempVideo = null;
        Path tempCover = null;
        try {
            // 1. 下载视频到临时文件
            String fullUrl = videoUrl.startsWith("/") ? "http://localhost:" + serverPort + videoUrl : videoUrl;
            tempVideo = Files.createTempFile("douyin-video-", ".mp4");
            try (InputStream in = new URL(fullUrl).openStream();
                 FileOutputStream out = new FileOutputStream(tempVideo.toFile())) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }

            // 2. FFmpeg 提取第一帧 (0.5 秒处)
            tempCover = Files.createTempFile("douyin-cover-", ".jpg");
            String ffmpeg = resolveFfmpeg();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y",
                    "-i", tempVideo.toAbsolutePath().toString(),
                    "-ss", "00:00:00.500",
                    "-vframes", "1",
                    "-q:v", "2",
                    tempCover.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished || !tempCover.toFile().exists() || tempCover.toFile().length() == 0) {
                log.warn("FFmpeg 封面提取失败, 请确认 FFmpeg 已安装");
                return null;
            }

            // 3. 上传封面到 MinIO
            byte[] coverBytes = Files.readAllBytes(tempCover);
            String objectName = UUID.randomUUID() + ".jpg";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketImage())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(coverBytes), coverBytes.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketImage())
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );

        } catch (Exception e) {
            log.warn("封面提取失败: {}", e.getMessage());
            return null;
        } finally {
            // 清理临时文件
            if (tempVideo != null) try { Files.deleteIfExists(tempVideo); } catch (IOException ignored) {}
            if (tempCover != null) try { Files.deleteIfExists(tempCover); } catch (IOException ignored) {}
        }
    }
}
