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

        // 3. 检测视频是否有音轨 + 视频编码格式
        boolean videoHasAudio = probeAudioStream(videoPath);
        String videoCodec = probeVideoCodec(videoPath);

        // 4. 构建 ffmpeg 命令
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

        String bgmFilter = String.format(
                "[1:a]atrim=start=%f,afade=t=in:st=0:d=1,afade=t=out:st=%s:d=2,volume=%f[a1]",
                bgmStartOffset, fadeOutStart, bgmVolume);
        if (videoHasAudio) {
            cmdList.add(bgmFilter + ";" +
                    String.format("[0:a]volume=%f[a0];", originalVol) +
                    "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[a]");
            log.info("[合成] 视频有音轨, 混音模式");
        } else {
            cmdList.add(bgmFilter + ";" +
                    String.format("anullsrc=r=44100:cl=stereo,atrim=duration=%.3f,volume=%.1f[a0];",
                            outDuration + 1, originalVol) +
                    "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[a]");
            log.info("[合成] 视频无音轨, 使用静音源");
        }

        cmdList.add("-map");
        cmdList.add("0:v");
        cmdList.add("-map");
        cmdList.add("[a]");

        // 视频编码: h264 直接 copy, VP8/VP9/AV1 等需重编码
        if (isMp4CompatibleCodec(videoCodec)) {
            cmdList.add("-c:v");
            cmdList.add("copy");
            log.info("[合成] 视频编码 {}, 直接复制", videoCodec);
        } else {
            String vcodec = detectH264Encoder();
            cmdList.add("-c:v");
            cmdList.add(vcodec);
            cmdList.add("-preset");
            cmdList.add("fast");
            cmdList.add("-crf");
            cmdList.add("23");
            log.info("[合成] 视频编码 {} 不兼容MP4, 重编码为 h264 ({})", videoCodec, vcodec);
        }

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

        log.info("[合成] ffmpeg 启动 trim=[{},{}] outDur={}",
                String.format("%.1f", trimStart), String.format("%.1f", trimEnd), String.format("%.1f", outDuration));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder ffmpegLog = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ffmpegLog.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(outputFile)) {
            // 输出 ffmpeg 完整日志以排查问题
            log.warn("[合成] ffmpeg 失败, 完整输出:\n{}", ffmpegLog);
            throw new RuntimeException("视频合成失败: exitCode=" + exitCode);
        }

        log.info("[合成] 完成: {} → {} (原声:{} BGM:{})",
                videoPath, outputFile, String.format("%.0f%%", originalVol * 100), String.format("%.0f%%", bgmVolume * 100));
        return musicBaseUrl + "/" + outName;
    }

    /** 用 ffprobe 检测视频是否包含音频流 */
    private boolean probeAudioStream(String videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-select_streams", "a",
                    "-show_entries", "stream=codec_type",
                    "-of", "csv=p=0",
                    videoPath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                return line != null && line.contains("audio");
            }
        } catch (Exception e) {
            log.warn("[合成] ffprobe 检测音轨失败, 假定有音轨: {}", e.getMessage());
            return true; // 兜底按有音轨处理
        }
    }

    /** 用 ffprobe 检测视频编码格式 (如 h264, vp8, vp9, hevc) */
    private String probeVideoCodec(String videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name",
                    "-of", "csv=p=0",
                    videoPath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                return line != null ? line.trim() : "unknown";
            }
        } catch (Exception e) {
            log.warn("[合成] ffprobe 检测编码失败: {}", e.getMessage());
            return "unknown";
        }
    }

    /** MP4 容器原生支持的编码可以直接 copy, 不需要重编码 */
    private boolean isMp4CompatibleCodec(String codec) {
        return codec != null && (
                codec.equals("h264") || codec.equals("avc1") ||
                codec.equals("hevc") || codec.equals("hvc1") || codec.equals("hev1") ||
                codec.equals("mpeg4") || codec.equals("msmpeg4") ||
                codec.equals("mjpeg")
        );
    }

    /**
     * 检测可用 h264 编码器。
     * 先用 ffmpeg 快速测试硬件编码器是否真正可用 (nvemc API 需要驱动 >=570, amf 需要 AMD 显卡),
     * 不行就退回 libx264 软件编码。
     */
    private String detectH264Encoder() {
        if (testEncoder("h264_nvenc")) return "h264_nvenc";
        if (testEncoder("h264_amf"))  return "h264_amf";
        return "libx264";
    }

    /** 用 2x2 纯色帧做一次快速编码测试, 确认编码器在运行时真正可用 */
    private boolean testEncoder(String encoder) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-f", "lavfi", "-i", "color=c=black:s=2x2:d=0.1",
                    "-c:v", encoder,
                    "-f", "null", "-"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
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

        log.info("[裁剪] ffmpeg: {} ss={} t={}", String.join(" ", cmd),
                String.format("%.1f", trimStart), String.format("%.1f", outDuration));
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
