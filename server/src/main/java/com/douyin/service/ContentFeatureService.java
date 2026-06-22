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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ContentFeatureService extends ServiceImpl<VideoContentMapper, VideoContent> {

    private final VideoExposureMapper exposureMapper;
    private final UserContentProfileMapper profileMapper;
    private final VideoMapper videoMapper;
    private final UserProfileService userProfileService;
    private final VideoTagService videoTagService;
    private final ObjectMapper objectMapper;

    // 单线程串行队列 — 稳定优先，不限时间
    private final LinkedBlockingQueue<Video> pendingQueue = new LinkedBlockingQueue<>();
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "feature-extract-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    private static final int MAX_RETRIES = RecommendationConfig.FEATURE_MAX_RETRIES;
    private static final long BASE_DELAY_MS = RecommendationConfig.FEATURE_BASE_DELAY_MS;

    public ContentFeatureService(VideoContentMapper contentMapper,
                                  VideoExposureMapper exposureMapper,
                                  UserContentProfileMapper profileMapper,
                                  VideoMapper videoMapper,
                                  UserProfileService userProfileService,
                                  VideoTagService videoTagService) {
        this.exposureMapper = exposureMapper;
        this.profileMapper = profileMapper;
        this.videoMapper = videoMapper;
        this.userProfileService = userProfileService;
        this.videoTagService = videoTagService;
        this.objectMapper = new ObjectMapper();
    }

    // ===================== 生命周期 =====================

    @PostConstruct
    public void startup() {
        workerRunning.set(true);
        workerExecutor.submit(() -> {
            recoverPending();
            workerLoop();
        });
        log.info("特征提取后台线程已启动");
    }

    @PreDestroy
    public void shutdown() {
        workerRunning.set(false);
        workerExecutor.shutdownNow();
        log.info("特征提取后台线程已关闭, 队列残留={}", pendingQueue.size());
    }

    /** 启动时找出所有需要提取特征但还未成功的视频 */
    private void recoverPending() {
        try {
            // 查找 extract_status 为 0(排队)/2(失败)/3(处理中) 的记录
            List<VideoContent> pending = baseMapper.selectList(
                    new LambdaQueryWrapper<VideoContent>()
                            .in(VideoContent::getExtractStatus, 0, 2, 3));
            for (VideoContent vc : pending) {
                Video video = videoMapper.selectById(vc.getVideoId());
                if (video != null) {
                    pendingQueue.add(video);
                    log.info("恢复未完成任务: videoId={}", video.getId());
                }
            }
            // 查找有 video 但没有 video_content 记录的作品
            List<Long> existingIds = baseMapper.selectList(null).stream()
                    .map(VideoContent::getVideoId).toList();
            List<Video> allVideos = videoMapper.selectList(null);
            for (Video v : allVideos) {
                if (!existingIds.contains(v.getId())) {
                    pendingQueue.add(v);
                    log.info("恢复缺失特征: videoId={}", v.getId());
                }
            }
        } catch (Exception e) {
            log.error("恢复未完成任务失败", e);
        }
    }

    // ===================== 对外入口 =====================

    /** 发布作品后调用 — 仅入队，立即返回 */
    public void extractAsync(Video video) {
        // 创建排队状态记录
        VideoContent pending = new VideoContent();
        pending.setVideoId(video.getId());
        pending.setExtractStatus(0);
        try {
            saveOrUpdate(pending);
        } catch (Exception e) {
            log.warn("写入排队状态失败: videoId={}", video.getId(), e);
        }
        pendingQueue.add(video);
        log.info("已加入特征提取队列: videoId={} type={} 队列长度≈{}",
                video.getId(), video.getType(), pendingQueue.size());
    }

    /** 手动重提单个视频 */
    public boolean reExtract(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) return false;
        VideoContent reset = new VideoContent();
        reset.setVideoId(videoId);
        reset.setExtractStatus(0);
        saveOrUpdate(reset);
        pendingQueue.add(video);
        log.info("手动重提特征提取: videoId={}", videoId);
        return true;
    }

    /** 批量重提所有失败的视频 (extract_status=2 或无记录) */
    public int reExtractAllFailed() {
        int count = 0;
        // 状态为 2(失败) 的记录
        List<VideoContent> failed = baseMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .eq(VideoContent::getExtractStatus, 2));
        for (VideoContent vc : failed) {
            Video video = videoMapper.selectById(vc.getVideoId());
            if (video != null) {
                vc.setExtractStatus(0);
                saveOrUpdate(vc);
                pendingQueue.add(video);
                count++;
            }
        }
        // 有 video 但没有 video_content 记录的作品
        List<Long> existingIds = baseMapper.selectList(null).stream()
                .map(VideoContent::getVideoId).toList();
        List<Video> allVideos = videoMapper.selectList(null);
        for (Video v : allVideos) {
            if (!existingIds.contains(v.getId())) {
                VideoContent vc = new VideoContent();
                vc.setVideoId(v.getId());
                vc.setExtractStatus(0);
                saveOrUpdate(vc);
                pendingQueue.add(v);
                count++;
            }
        }
        // 状态为 0(排队) 或 3(处理中) 但是没有实际特征数据的也重试
        List<VideoContent> stalled = baseMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .in(VideoContent::getExtractStatus, 0, 3));
        for (VideoContent vc : stalled) {
            Video video = videoMapper.selectById(vc.getVideoId());
            if (video != null && !pendingQueue.contains(video)) {
                pendingQueue.add(video);
                count++;
            }
        }
        log.info("批量重提特征提取: 共{}条", count);
        return count;
    }

    /** 查看队列状态 */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("queueSize", pendingQueue.size());
        status.put("workerRunning", workerRunning.get());
        return status;
    }

    // ===================== 后台工作线程 =====================

    private void workerLoop() {
        log.info("特征提取工作线程开始运行");
        while (workerRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Video video = pendingQueue.poll(5, TimeUnit.SECONDS);
                if (video == null) continue;
                processOne(video);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("特征提取工作线程异常", e);
            }
        }
        log.info("特征提取工作线程退出");
    }

    /** 处理单个视频，带指数退避重试 */
    private void processOne(Video video) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                updateStatus(video.getId(), 3, attempt);
                log.info("开始特征提取: videoId={} type={} 第{}次尝试",
                        video.getId(), video.getType(), attempt);

                Map<String, Object> features = extractFeaturesSync(video);
                saveFeatures(features);
                log.info("特征提取成功: videoId={} 第{}次尝试", video.getId(), attempt);
                return;

            } catch (Exception e) {
                log.error("特征提取失败: videoId={} 第{}/{}次尝试 error={}",
                        video.getId(), attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                    log.info("将在 {}s 后重试 videoId={}", delay / 1000, video.getId());
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        updateStatus(video.getId(), 2, attempt);
                        return;
                    }
                }
            }
        }
        // 所有重试都用完
        updateStatus(video.getId(), 2, MAX_RETRIES);
        log.error("特征提取最终失败(已重试{}次): videoId={}", MAX_RETRIES, video.getId());
    }

    // ===================== 同步调用 Python =====================

    /** 同步调用 Python 脚本，解析 stdout 中 __RESULT__ 行，返回特征 Map */
    private Map<String, Object> extractFeaturesSync(Video video) throws Exception {
        String pythonDir = findPythonDir();
        String script = pythonDir + "/extract_video_features.py";
        String python = findPython(pythonDir);
        String type = video.getType();

        List<String> cmd;
        if ("image".equals(type)) {
            String imageUrlsJson = video.getImageUrls();
            List<String> urlList = new ArrayList<>();
            if (imageUrlsJson != null && !imageUrlsJson.isEmpty()) {
                try {
                    urlList = objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {});
                } catch (Exception e) {
                    // 不是 JSON 数组，当作单 URL
                    urlList.add(imageUrlsJson);
                }
            }
            if (urlList.isEmpty()) {
                String fallbackUrl = video.getVideoUrl();
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    urlList.add(fallbackUrl);
                } else {
                    log.warn("图文 videoId={} 无 imageUrls 也无 videoUrl，跳过", video.getId());
                    throw new RuntimeException("图文模式缺少图片URL");
                }
            }
            // 用逗号分隔传 URL 列表，避免 JSON 数组在 Windows shell 中引号被破坏
            String urlsArg = String.join(",", urlList);
            cmd = new ArrayList<>(Arrays.asList(
                    python, script, "--mode", "image",
                    "--image-urls", urlsArg,
                    "--video-id", String.valueOf(video.getId()),
                    "--api-base", "http://localhost:9191"
            ));
        } else if ("text".equals(type)) {
            cmd = new ArrayList<>(Arrays.asList(
                    python, script, "--mode", "text",
                    "--video-id", String.valueOf(video.getId()),
                    "--api-base", "http://localhost:9191"
            ));
        } else {
            cmd = new ArrayList<>(Arrays.asList(
                    python, script, "--mode", "video",
                    "--video-url", video.getVideoUrl(),
                    "--video-id", String.valueOf(video.getId()),
                    "--api-base", "http://localhost:9191"
            ));
        }

        if (video.getDesc() != null && !video.getDesc().isEmpty()) {
            cmd.add("--desc"); cmd.add(video.getDesc());
        }
        if (video.getMusicTitle() != null && !video.getMusicTitle().isEmpty()
                && !"text".equals(type)) {
            cmd.add("--music-title"); cmd.add(video.getMusicTitle());
        }

        log.info("启动特征提取: mode={} videoId={} cmd={}", type, video.getId(), String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(pythonDir));
        pb.redirectErrorStream(true);
        pb.environment().put("HF_ENDPOINT", "https://hf-mirror.com");
        pb.environment().put("HF_HUB_OFFLINE", "1");
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = pb.start();
        StringBuilder allOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Python] {}", line);
                allOutput.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();
        log.info("Python 进程退出: videoId={} exitCode={}", video.getId(), exitCode);

        if (exitCode != 0) {
            throw new RuntimeException("Python exited with code " + exitCode
                    + ", output: " + allOutput.substring(Math.max(0, allOutput.length() - 500)));
        }

        // 从 stdout 中提取 __RESULT__{json} 行
        String[] lines = allOutput.toString().split("\n");
        for (String line : lines) {
            if (line.startsWith("__RESULT__")) {
                String json = line.substring("__RESULT__".length());
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(json, Map.class);
                return result;
            }
        }
        throw new RuntimeException("Python 输出中未找到 __RESULT__ 行");
    }

    // ===================== 特征保存 =====================

    /** 将 Python 返回的特征 Map 写入 t_video_content */
    public void saveFeatures(Map<String, Object> features) {
        Long videoId = Long.valueOf(features.get("video_id").toString());
        VideoContent content = new VideoContent();
        content.setVideoId(videoId);

        // === 视觉特征 ===
        content.setVisualDesc((String) features.get("visual_desc"));
        content.setSceneTags(toJson(features.get("scene_tags")));
        content.setObjectTags(toJson(features.get("object_tags")));
        content.setVisualEmbedding(toJson(features.get("visual_embedding")));

        // === 开放词汇多标签分类 ===
        content.setCategories(toJson(features.get("categories")));
        content.setMood((String) features.get("mood"));
        content.setStyle((String) features.get("style"));
        content.setQualityLabel((String) features.get("quality_label"));
        content.setMusicGenre((String) features.get("music_genre"));
        content.setContentAttributes(toJson(features.get("content_attributes")));

        // === ASR 语音特征 ===
        content.setTranscript((String) features.get("transcript"));
        content.setAsrKeywords(toJson(features.get("asr_keywords")));
        content.setHasSpeech(toBool(features.get("has_speech")));

        // === 人脸特征 ===
        content.setFaceCount(toDouble(features.get("face_count")));
        content.setFaceRatio(toDouble(features.get("face_ratio")));
        content.setIsCloseup(toBool(features.get("is_closeup")));
        content.setIsSinglePerson(toBool(features.get("is_single_person")));
        content.setIsMultiPerson(toBool(features.get("is_multi_person")));

        // === 增强音频特征 ===
        content.setMusicBpm(toDouble(features.get("music_bpm")));
        content.setMusicKey((String) features.get("music_key"));
        content.setMusicEnergy(toDouble(features.get("music_energy")));
        content.setMusicValence(toDouble(features.get("music_valence")));
        content.setMusicSpectral(toDouble(features.get("music_spectral")));
        content.setMusicRolloff(toDouble(features.get("music_rolloff")));
        content.setMusicBandwidth(toDouble(features.get("music_bandwidth")));
        content.setMusicZcr(toDouble(features.get("music_zcr")));
        content.setMusicOnsetRate(toDouble(features.get("music_onset_rate")));
        content.setMusicMfcc(toJson(features.get("music_mfcc")));
        content.setAudioHasMusic(toBool(features.get("audio_has_music")));

        // === 文本特征 ===
        content.setKeywords(toJson(features.get("keywords")));
        content.setTextCategory((String) features.get("text_category"));
        content.setTextEmbedding(toJson(features.get("text_embedding")));

        // === 融合向量 & 质量 ===
        content.setContentVector(toJson(features.get("content_vector")));
        content.setQualityScore(toDouble(features.get("quality_score")));

        // === 元数据 ===
        content.setExtractStatus(toInt(features.get("extract_status"), 1));
        content.setExtractTimeMs(toInt(features.get("extract_time_ms"), 0));

        saveOrUpdate(content);
        log.info("特征已保存: videoId={} category={} status={} mood={} hasSpeech={} faceCount={}",
                videoId, content.getTextCategory(), content.getExtractStatus(),
                content.getMood(), content.getHasSpeech(), content.getFaceCount());

        // 动态标签: 将 AI 提取的标签写入 t_video_tag
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            try {
                videoTagService.saveInitialTags(video, features);
            } catch (Exception e) {
                log.warn("初始标签保存失败: videoId={}", videoId, e);
            }

            // 内容相似度标签继承: 从相似老视频继承行为标签
            try {
                inheritTagsFromSimilar(videoId, features);
            } catch (Exception e) {
                log.warn("标签继承失败: videoId={}", videoId, e);
            }
        }
    }

    /** 从相似老视频继承行为标签 (comment/search/cowatch/co_search) */
    private void inheritTagsFromSimilar(Long videoId, Map<String, Object> features) {
        String contentVectorJson = toJson(features.get("content_vector"));
        if (contentVectorJson == null) return;

        List<Double> newVec = parseVector(contentVectorJson);
        if (newVec.isEmpty()) return;

        // 查找最近有 content_vector 的视频 (排除自身)
        List<VideoContent> recentContents = baseMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .isNotNull(VideoContent::getContentVector)
                        .ne(VideoContent::getVideoId, videoId)
                        .orderByDesc(VideoContent::getVideoId)
                        .last("LIMIT " + RecommendationConfig.TAG_INHERIT_POOL_SIZE));

        if (recentContents.isEmpty()) return;

        // 余弦相似度 top-5 (小顶堆)
        record SimPair(long vid, double sim) {}
        var topK = new PriorityQueue<SimPair>(Comparator.comparingDouble(p -> p.sim));

        for (VideoContent vc : recentContents) {
            List<Double> vec = parseVector(vc.getContentVector());
            if (vec.isEmpty()) continue;
            double sim = cosineSimilarity(newVec, vec);
            if (topK.size() < RecommendationConfig.TAG_INHERIT_TOP_K) {
                topK.add(new SimPair(vc.getVideoId(), sim));
            } else if (sim > topK.peek().sim) {
                topK.poll();
                topK.add(new SimPair(vc.getVideoId(), sim));
            }
        }

        if (topK.isEmpty()) return;

        // 继承行为标签 (排除 ai 和 trend)
        int inherited = 0;
        for (SimPair sp : topK) {
            List<Map<String, Object>> tags = videoTagService.getTags(sp.vid);
            for (Map<String, Object> tag : tags) {
                String source = (String) tag.get("source");
                if (source == null || source.contains("ai") || source.contains("trend")) continue;

                Object confObj = tag.get("confidence");
                double confidence = confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.5;
                if (confidence < RecommendationConfig.TAG_INHERIT_MIN_CONF) continue;

                double adjustedConf = confidence * sp.sim;
                if (adjustedConf < RecommendationConfig.TAG_INHERIT_MIN_ADJUSTED) continue;

                videoTagService.inheritTag(videoId, (String) tag.get("tag"), adjustedConf, source);
                inherited++;
            }
        }

        if (inherited > 0) {
            log.info("标签继承: videoId={} 继承{}个标签 sims={}",
                    videoId, inherited, topK.stream().map(p -> String.format("%.3f", p.sim)).toList());
        }
    }

    private List<Double> parseVector(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size() || a.isEmpty()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void updateStatus(Long videoId, int status, int attempt) {
        VideoContent content = new VideoContent();
        content.setVideoId(videoId);
        content.setExtractStatus(status);
        try {
            saveOrUpdate(content);
        } catch (Exception e) {
            log.warn("更新提取状态失败: videoId={} status={}", videoId, status, e);
        }
    }

    // ===================== 目录 & Python 查找 =====================

    private String findPythonDir() {
        String userDir = System.getProperty("user.dir");
        String[] candidates = {
                userDir + "/python",
                userDir + "/server/python",
                new java.io.File("../python").getAbsolutePath()
        };
        for (String dir : candidates) {
            java.io.File f = new java.io.File(dir);
            if (f.isDirectory() && new java.io.File(dir + "/extract_video_features.py").exists()) {
                return f.getAbsolutePath();
            }
        }
        log.warn("未找到 python 目录，使用兜底路径: {}", userDir + "/server/python");
        return userDir + "/server/python";
    }

    private String findPython(String pythonDir) {
        String uvPython = pythonDir + "/.venv/Scripts/python.exe";
        if (new java.io.File(uvPython).exists()) return uvPython;

        for (String candidate : new String[]{"python3", "python", "py"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                Process p = pb.start();
                if (p.waitFor() == 0) return candidate;
            } catch (Exception ignored) {}
        }
        return "python";
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

    public UserContentProfile getProfile(Long userId) {
        return userProfileService.getOrCreate(userId);
    }

    public void onWatch(Long userId, Long videoId, Long authorId,
                        double watchDurationSec, double videoDurationSec,
                        String trafficSource, String sessionId, double swipeSeconds) {
        userProfileService.onWatch(userId, videoId, authorId,
                watchDurationSec, videoDurationSec, trafficSource, sessionId, swipeSeconds);
    }

    public void onLike(Long userId, Long videoId, Long authorId) {
        userProfileService.onLike(userId, videoId, authorId);
    }

    public void onCollect(Long userId, Long videoId, Long authorId) {
        userProfileService.onCollect(userId, videoId, authorId);
    }

    public void onShare(Long userId, Long videoId, Long authorId) {
        userProfileService.onShare(userId, videoId, authorId);
    }

    public void onComment(Long userId, Long videoId, Long authorId) {
        userProfileService.onComment(userId, videoId, authorId);
    }

    public void onFollow(Long userId, Long followId) {
        userProfileService.onFollow(userId, followId);
    }

    public void onSearch(Long userId, String keyword) {
        userProfileService.onSearch(userId, keyword);
    }

    public void onProfileVisit(Long userId, Long visitedUserId) {
        userProfileService.onProfileVisit(userId, visitedUserId);
    }

    public void onCommentLike(Long userId, Long commentId, Long videoId) {
        userProfileService.onCommentLike(userId, commentId, videoId);
    }

    public void onCommentDislike(Long userId, Long commentId, Long videoId) {
        userProfileService.onCommentDislike(userId, commentId, videoId);
    }

    public void rebuildProfile(Long userId) {
        userProfileService.rebuildProfile(userId);
    }

    @Deprecated
    @Transactional
    public void updateProfileOnInteract(Long userId, Long videoId, String actionType, double weight) {
        Video video = videoMapper.selectById(videoId);
        Long authorId = video != null ? video.getAuthorUserId() : null;
        Double videoDur = video != null ? video.getDuration() : 0;
        Double completionRate = videoDur != null && videoDur > 0 ? 1.0 : 0;

        switch (actionType) {
            case "view":
                onWatch(userId, videoId, authorId,
                        videoDur != null ? videoDur * completionRate : 15.0,
                        videoDur != null ? videoDur : 30.0,
                        "HOME_RECOMMEND", null, 0);
                break;
            case "like": onLike(userId, videoId, authorId); break;
            case "collect": onCollect(userId, videoId, authorId); break;
            case "share": onShare(userId, videoId, authorId); break;
            case "comment": onComment(userId, videoId, authorId); break;
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

    private Boolean toBool(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Boolean b) return b;
        String s = obj.toString().toLowerCase();
        return "true".equals(s) || "1".equals(s);
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
