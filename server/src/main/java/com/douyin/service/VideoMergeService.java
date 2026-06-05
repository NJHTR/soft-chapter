package com.douyin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * 视频+音乐合成服务 (基于 ffmpeg)
 *
 * 流程: 下载BGM → ffmpeg混音 → 输出最终视频
 */
@Slf4j
@Service
public class VideoMergeService {

    @org.springframework.beans.factory.annotation.Value("${server.port:9191}")
    private int serverPort;

    @org.springframework.beans.factory.annotation.Value("${music.local.dir:${user.dir}/music}")
    private String musicDir;

    @org.springframework.beans.factory.annotation.Value("${music.local.base-url:/music}")
    private String musicBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 将视频与BGM合成为最终视频，支持视频裁剪
     *
     * @param videoPath      原始视频路径
     * @param musicUrl       BGM播放URL (可下载)
     * @param musicTitle     BGM名称
     * @param videoDuration  视频时长(秒)
     * @param bgmVolume      BGM音量 0-1, 0.7=70%
     * @param bgmStartOffset BGM起始偏移(秒)
     * @param trimStart      裁剪起始(秒), 0 表示不裁剪
     * @param trimEnd        裁剪结束(秒), 0 表示不裁剪
     * @return 合成后的视频路径
     */
    public String merge(String videoPath, String musicUrl, String musicTitle,
                        double videoDuration, double bgmVolume, double bgmStartOffset,
                        double trimStart, double trimEnd) throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "douyin_merge_" + UUID.randomUUID());
        Files.createDirectories(tempDir);

        // 1. 下载 BGM
        Path musicFile = tempDir.resolve("bgm.mp3");
        log.info("[合成] 下载BGM: {} → {}", musicTitle, musicFile);
        downloadFile(musicUrl, musicFile);

        // 2. 计算实际输出时长
        boolean doTrim = trimEnd > trimStart && trimEnd > 0;
        double outDuration = doTrim ? (trimEnd - trimStart) : videoDuration;
        String fadeOutStart = String.format("%.1f", Math.max(0, outDuration - 2));
        double originalVol = 1.0 - bgmVolume * 0.6;

        // 3. 构建 ffmpeg 命令
        String outName = "merged_" + UUID.randomUUID() + ".mp4";
        Path musicDirPath = Path.of(musicDir);
        Files.createDirectories(musicDirPath);
        Path outputFile = musicDirPath.resolve(outName);

        java.util.List<String> cmdList = new java.util.ArrayList<>();
        cmdList.add("ffmpeg");
        cmdList.add("-y");
        if (doTrim) {
            cmdList.add("-ss");
            cmdList.add(String.format("%.3f", trimStart));
        }
        cmdList.add("-i");
        cmdList.add(videoPath);
        cmdList.add("-i");
        cmdList.add(musicFile.toString());
        cmdList.add("-filter_complex");
        cmdList.add(String.format(
                "[1:a]atrim=%f,afade=t=in:st=0:d=1,afade=t=out:st=%s:d=2,volume=%f[a1];" +
                "[0:a]volume=%f[a0];" +
                "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[a]",
                bgmStartOffset, fadeOutStart, bgmVolume, originalVol));
        cmdList.add("-map");
        cmdList.add("0:v");
        cmdList.add("-map");
        cmdList.add("[a]");
        cmdList.add("-c:v");
        cmdList.add("copy");
        cmdList.add("-c:a");
        cmdList.add("aac");
        cmdList.add("-b:a");
        cmdList.add("192k");
        if (doTrim) {
            cmdList.add("-t");
            cmdList.add(String.format("%.3f", outDuration));
        }
        cmdList.add(outputFile.toString());

        String[] cmd = cmdList.toArray(new String[0]);

        log.info("[合成] ffmpeg: {} trim=[{:.1f},{:.1f}] outDur={:.1f}", String.join(" ", cmd), trimStart, trimEnd, outDuration);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[ffmpeg] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(outputFile)) {
            throw new RuntimeException("视频合成失败: exitCode=" + exitCode);
        }

        log.info("[合成] 完成: {} → {} (原声:{} BGM:{})",
                videoPath, outputFile, String.format("%.0f%%", originalVol * 100), String.format("%.0f%%", bgmVolume * 100));
        return musicBaseUrl + "/" + outName;
    }

    /**
     * 对视频进行裁剪 (无BGM合成，纯裁剪)
     *
     * @param videoPath 原始视频路径
     * @param trimStart 裁剪起始(秒)
     * @param trimEnd   裁剪结束(秒)
     * @return 裁剪后的视频路径
     */
    public String trimVideo(String videoPath, double trimStart, double trimEnd) throws Exception {
        String outName = "trimmed_" + UUID.randomUUID() + ".mp4";
        Path musicDirPath = Path.of(musicDir);
        Files.createDirectories(musicDirPath);
        Path outputFile = musicDirPath.resolve(outName);

        double outDuration = trimEnd - trimStart;
        String resolvedPath = videoPath.startsWith("/") ? "http://localhost:" + serverPort + videoPath : videoPath;
        String[] cmd = {
                "ffmpeg", "-y",
                "-ss", String.format("%.3f", trimStart),
                "-i", resolvedPath,
                "-t", String.format("%.3f", outDuration),
                "-c:v", "copy",
                "-c:a", "copy",
                outputFile.toString()
        };

        log.info("[裁剪] ffmpeg: {} ss={:.1f} t={:.1f}", String.join(" ", cmd), trimStart, outDuration);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[ffmpeg] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(outputFile)) {
            throw new RuntimeException("视频裁剪失败: exitCode=" + exitCode);
        }

        log.info("[裁剪] 完成: {} → {}", videoPath, outputFile);
        return musicBaseUrl + "/" + outName;
    }

    /** 仅截取BGM片段 (用于预览) */
    public String trimMusic(String musicUrl, double startSec, double durationSec) throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "douyin_trim_" + UUID.randomUUID());
        Files.createDirectories(tempDir);

        Path musicFile = tempDir.resolve("bgm.mp3");
        downloadFile(musicUrl, musicFile);

        Path outputFile = tempDir.resolve("trimmed.mp3");
        String[] cmd = {
                "ffmpeg", "-y",
                "-ss", String.valueOf(startSec),
                "-i", musicFile.toString(),
                "-t", String.valueOf(durationSec),
                "-c:a", "copy",
                outputFile.toString()
        };

        new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();

        if (Files.exists(outputFile)) {
            return outputFile.toString();
        }
        return null;
    }

    private void downloadFile(String url, Path dest) throws Exception {
        // 相对路径补全为本地地址
        String fullUrl = url.startsWith("/") ? "http://localhost:" + serverPort + url : url;
        log.info("[合成] 下载: {}", fullUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        Files.copy(response.body(), dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
