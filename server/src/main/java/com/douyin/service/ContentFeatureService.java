package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Video;
import com.douyin.entity.VideoContent;
import com.douyin.entity.VideoExposure;
import com.douyin.entity.UserContentProfile;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.UserContentProfileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ContentFeatureService extends ServiceImpl<VideoContentMapper, VideoContent> {

    private final VideoExposureMapper exposureMapper;
    private final UserContentProfileMapper profileMapper;
    private final VideoMapper videoMapper;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    public ContentFeatureService(VideoContentMapper contentMapper,
                                  VideoExposureMapper exposureMapper,
                                  UserContentProfileMapper profileMapper,
                                  VideoMapper videoMapper,
                                  UserProfileService userProfileService) {
        this.exposureMapper = exposureMapper;
        this.profileMapper = profileMapper;
        this.videoMapper = videoMapper;
        this.userProfileService = userProfileService;
        this.objectMapper = new ObjectMapper();
    }

    // ===================== 特征提取 =====================

    /** 异步调用 Python 特征提取脚本 */
    public void extractAsync(Video video) {
        CompletableFuture.runAsync(() -> {
            try {
                extractFeatures(video);
            } catch (Exception e) {
                log.error("特征提取失败: videoId={}", video.getId(), e);
                VideoContent err = new VideoContent();
                err.setVideoId(video.getId());
                err.setExtractStatus(2);
                saveOrUpdate(err);
            }
        });
    }

    /** 同步调用 Python 脚本提取特征 */
    public void extractFeatures(Video video) throws Exception {
        String pythonDir = findPythonDir();
        String script = pythonDir + "/extract_video_features.py";
        String python = findPython(pythonDir);

        List<String> cmd = new ArrayList<>(Arrays.asList(
                python, script,
                "--video-url", video.getVideoUrl(),
                "--video-id", String.valueOf(video.getId()),
                "--api-base", "http://localhost:9191"
        ));
        if (video.getDesc() != null && !video.getDesc().isEmpty()) {
            cmd.add("--desc");
            cmd.add(video.getDesc());
        }
        if (video.getMusicTitle() != null && !video.getMusicTitle().isEmpty()) {
            cmd.add("--music-title");
            cmd.add(video.getMusicTitle());
        }

        log.info("启动特征提取: videoId={} dir={} python={}", video.getId(), pythonDir, python);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(pythonDir));
        pb.redirectErrorStream(true);
        // Hugging Face 国内镜像，避免模型下载超时
        pb.environment().put("HF_ENDPOINT", "https://hf-mirror.com");
        // 强制 Python 输出 UTF-8，避免 Windows 乱码
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Python] {}", line);
            }
        }
        int exitCode = process.waitFor();
        log.info("特征提取完成: videoId={} exitCode={}", video.getId(), exitCode);
    }

    /** 定位 python 目录，尝试多种路径 */
    private String findPythonDir() {
        String userDir = System.getProperty("user.dir");
        // 尝试 server/python (项目根目录为父级时)
        String[] candidates = {
                userDir + "/python",              // user.dir 即为 server 目录
                userDir + "/server/python",       // user.dir 为项目根目录
                new java.io.File("../python").getAbsolutePath()
        };
        for (String dir : candidates) {
            java.io.File f = new java.io.File(dir);
            if (f.isDirectory() && new java.io.File(dir + "/extract_video_features.py").exists()) {
                return f.getAbsolutePath();
            }
        }
        return userDir + "/server/python"; // 最终兜底
    }

    /** 查找可用的 Python：优先 uv venv，其次全局 */
    private String findPython(String pythonDir) {
        // 先尝试 uv venv 中的 python
        String uvPython = pythonDir + "/.venv/Scripts/python.exe";
        if (new java.io.File(uvPython).exists()) return uvPython;

        // 再尝试全局命令
        for (String candidate : new String[]{"python3", "python", "py"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                Process p = pb.start();
                if (p.waitFor() == 0) return candidate;
            } catch (Exception ignored) {}
        }
        // uv run 方式兜底: uv run python
        return "python";
    }

    // ===================== 特征保存（Python 回调） =====================

    @Transactional
    public void saveFeatures(Map<String, Object> features) {
        Long videoId = Long.valueOf(features.get("video_id").toString());
        VideoContent content = new VideoContent();
        content.setVideoId(videoId);

        content.setVisualDesc((String) features.get("visual_desc"));
        content.setSceneTags(toJson(features.get("scene_tags")));
        content.setObjectTags(toJson(features.get("object_tags")));
        content.setVisualEmbedding(toJson(features.get("visual_embedding")));
        content.setMusicBpm(toDouble(features.get("music_bpm")));
        content.setMusicKey((String) features.get("music_key"));
        content.setMusicEnergy(toDouble(features.get("music_energy")));
        content.setMusicValence(toDouble(features.get("music_valence")));
        content.setMusicSpectral(toDouble(features.get("music_spectral")));
        content.setMusicMfcc(toJson(features.get("music_mfcc")));
        content.setKeywords(toJson(features.get("keywords")));
        content.setTextCategory((String) features.get("text_category"));
        content.setTextEmbedding(toJson(features.get("text_embedding")));
        content.setContentVector(toJson(features.get("content_vector")));
        content.setQualityScore(toDouble(features.get("quality_score")));
        content.setExtractStatus(toInt(features.get("extract_status"), 1));
        content.setExtractTimeMs(toInt(features.get("extract_time_ms"), 0));

        saveOrUpdate(content);
        log.info("特征已保存: videoId={}, category={}, keywords={}",
                videoId, content.getTextCategory(),
                features.get("keywords") != null ? features.get("keywords").toString().substring(0, Math.min(60, features.get("keywords").toString().length())) : "");
    }

    // ===================== 曝光记录 =====================

    public void recordExposure(Long userId, Long videoId, double swipeSeconds) {
        VideoExposure e = new VideoExposure();
        e.setUserId(userId);
        e.setVideoId(videoId);
        e.setExposureTime(LocalDateTime.now());
        e.setSwipeSeconds(swipeSeconds);
        e.setClicked(0);
        exposureMapper.insert(e);
    }

    /** 批量记录曝光 */
    public void recordExposures(Long userId, List<Long> videoIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long vid : videoIds) {
            VideoExposure e = new VideoExposure();
            e.setUserId(userId);
            e.setVideoId(vid);
            e.setExposureTime(now);
            e.setSwipeSeconds(0.0);
            e.setClicked(0);
            try { exposureMapper.insert(e); } catch (Exception ignored) {}
        }
    }

    /** 获取用户 24h 内已曝光的视频 ID 列表 */
    public Set<Long> getRecentExposures(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return exposureMapper.selectList(new LambdaQueryWrapper<VideoExposure>()
                .eq(VideoExposure::getUserId, userId)
                .ge(VideoExposure::getExposureTime, since)
                .select(VideoExposure::getVideoId))
                .stream().map(VideoExposure::getVideoId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ===================== 用户画像 =====================

    /** 获取用户内容画像 (全行为链路) */
    public UserContentProfile getProfile(Long userId) {
        return userProfileService.getOrCreate(userId);
    }

    /** 观看视频后更新画像 (全信号) */
    public void onWatch(Long userId, Long videoId, Long authorId,
                        double watchDurationSec, double videoDurationSec,
                        String trafficSource, String sessionId, double swipeSeconds) {
        userProfileService.onWatch(userId, videoId, authorId,
                watchDurationSec, videoDurationSec, trafficSource, sessionId, swipeSeconds);
    }

    /** 点赞 */
    public void onLike(Long userId, Long videoId, Long authorId) {
        userProfileService.onLike(userId, videoId, authorId);
    }

    /** 收藏 */
    public void onCollect(Long userId, Long videoId, Long authorId) {
        userProfileService.onCollect(userId, videoId, authorId);
    }

    /** 分享 */
    public void onShare(Long userId, Long videoId, Long authorId) {
        userProfileService.onShare(userId, videoId, authorId);
    }

    /** 评论 */
    public void onComment(Long userId, Long videoId, Long authorId) {
        userProfileService.onComment(userId, videoId, authorId);
    }

    /** 关注 */
    public void onFollow(Long userId, Long followId) {
        userProfileService.onFollow(userId, followId);
    }

    /** 搜索 */
    public void onSearch(Long userId, String keyword) {
        userProfileService.onSearch(userId, keyword);
    }

    /** 浏览他人主页 */
    public void onProfileVisit(Long userId, Long visitedUserId) {
        userProfileService.onProfileVisit(userId, visitedUserId);
    }

    /** 评论点赞 */
    public void onCommentLike(Long userId, Long commentId, Long videoId) {
        userProfileService.onCommentLike(userId, commentId, videoId);
    }

    /** 评论点踩 */
    public void onCommentDislike(Long userId, Long commentId, Long videoId) {
        userProfileService.onCommentDislike(userId, commentId, videoId);
    }

    /** 重建全量画像 */
    public void rebuildProfile(Long userId) {
        userProfileService.rebuildProfile(userId);
    }

    /** @deprecated 使用 onWatch/onLike/onCollect 等细粒度方法替代 */
    @Deprecated
    @Transactional
    public void updateProfileOnInteract(Long userId, Long videoId, String actionType, double weight) {
        Video video = videoMapper.selectById(videoId);
        Long authorId = video != null ? video.getAuthorUserId() : null;
        Double videoDur = video != null ? video.getDuration() : 0;
        Double completionRate = videoDur != null && videoDur > 0 ? 1.0 : 0; // 默认假定完播

        switch (actionType) {
            case "view":
                onWatch(userId, videoId, authorId,
                        videoDur != null ? videoDur * completionRate : 15.0,
                        videoDur != null ? videoDur : 30.0,
                        "HOME_RECOMMEND", null, 0);
                break;
            case "like":
                onLike(userId, videoId, authorId);
                break;
            case "collect":
                onCollect(userId, videoId, authorId);
                break;
            case "share":
                onShare(userId, videoId, authorId);
                break;
            case "comment":
                onComment(userId, videoId, authorId);
                break;
            default:
                break;
        }
    }

    // ===================== 工具 =====================

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object obj, int defaultVal) {
        if (obj == null) return defaultVal;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }
}
