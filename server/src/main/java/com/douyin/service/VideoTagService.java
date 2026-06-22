package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.Video;
import com.douyin.entity.VideoContent;
import com.douyin.entity.VideoTag;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.VideoTagMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.mapper.UserContentProfileMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 动态标签服务 — 六路行为信号驱动标签自动增长/衰减
 *
 * 信号源:
 *   comment     — 评论聚合关键词
 *   search      — 搜索→观看关联
 *   cowatch     — 共观会话扩散
 *   co_search   — 猜你想搜回写
 *   ai          — AI 提取标签 (置信度被行为校准)
 *   trend       — 热榜趋势匹配
 */
@Slf4j
@Service
public class VideoTagService {

    private final VideoTagMapper tagMapper;
    private final VideoContentMapper contentMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoMapper videoMapper;
    private final UserContentProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    // === 信号累积器 (内存, 定时 flush 到 DB) ===

    /** videoId → { keyword → count } — 评论关键词聚合 */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, AtomicInteger>> commentSignals
            = new ConcurrentHashMap<>();

    /** videoId → { searchKeyword → [completionRates...] } — 搜索→观看关联 */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SearchWatchAccumulator>> searchWatchSignals
            = new ConcurrentHashMap<>();

    /** 共观会话追踪: sessionId → lastWatchedVideoId */
    private final ConcurrentHashMap<String, Long> sessionLastVideo = new ConcurrentHashMap<>();

    /** videoA → { videoB → pairCount } — 共观对累积 (videoA→videoB 表示用户看完A后看B) */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AtomicInteger>> coWatchPairs
            = new ConcurrentHashMap<>();

    // === 阈值常量 ===
    private static final int COMMENT_THRESHOLD = RecommendationConfig.TAG_COMMENT_THRESHOLD;
    private static final int SEARCH_THRESHOLD = RecommendationConfig.TAG_SEARCH_THRESHOLD;
    private static final int COWATCH_THRESHOLD = RecommendationConfig.TAG_COWATCH_THRESHOLD;
    private static final double MIN_WEIGHT = RecommendationConfig.TAG_MIN_WEIGHT;
    private static final double DECAY_FACTOR = RecommendationConfig.TAG_DECAY_FACTOR;

    // === 行为验证权重 ===
    private static final double VAL_COMPLETION_BONUS = RecommendationConfig.VAL_COMPLETION_BONUS;
    private static final double VAL_BOUNCE_PENALTY = RecommendationConfig.VAL_BOUNCE_PENALTY;
    private static final double VAL_LIKE_BONUS = RecommendationConfig.VAL_LIKE_BONUS;
    private static final double VAL_COMMENT_BONUS = RecommendationConfig.VAL_COMMENT_BONUS;

    public VideoTagService(VideoTagMapper tagMapper, VideoContentMapper contentMapper,
                           WatchHistoryMapper watchHistoryMapper,
                           VideoMapper videoMapper,
                           UserContentProfileMapper profileMapper) {
        this.tagMapper = tagMapper;
        this.contentMapper = contentMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.videoMapper = videoMapper;
        this.profileMapper = profileMapper;
        this.objectMapper = new ObjectMapper();
    }

    // ===================== 对外接口 (hook 点) =====================

    /** 评论创建时调用: 提取关键词, 累积信号 */
    public void onComment(Long videoId, String commentText) {
        if (commentText == null || commentText.trim().length() < RecommendationConfig.COMMENT_MIN_LEN || videoId == null) return;

        List<String> keywords = extractPhrases(commentText);
        if (keywords.isEmpty()) return;

        commentSignals.computeIfAbsent(videoId, k -> new ConcurrentHashMap<>());
        var signalMap = commentSignals.get(videoId);
        for (String kw : keywords) {
            signalMap.computeIfAbsent(kw, k -> new AtomicInteger(0)).incrementAndGet();
        }

        int totalSignals = signalMap.values().stream().mapToInt(AtomicInteger::get).sum();
        if (totalSignals >= COMMENT_THRESHOLD * 2) {
            // 达到阈值立即 flush 此视频
            flushCommentTags(videoId);
        }
    }

    /** 用户搜索后观看视频时调用: 建立搜索词→视频关联 */
    public void onSearchWatch(Long videoId, String keyword, double completionRate) {
        if (keyword == null || keyword.trim().length() < RecommendationConfig.KEYWORD_MIN_LEN || videoId == null) return;

        String kw = keyword.trim();
        searchWatchSignals.computeIfAbsent(videoId, k -> new ConcurrentHashMap<>());
        var signalMap = searchWatchSignals.get(videoId);
        signalMap.computeIfAbsent(kw, k -> new SearchWatchAccumulator()).add(completionRate);
    }

    /** 猜你想搜回写: 协同搜索高频词直接打标 */
    public void onCoSearchHint(Long videoId, String keyword, double score) {
        if (keyword == null || keyword.trim().length() < RecommendationConfig.KEYWORD_MIN_LEN || videoId == null) return;
        if (score < RecommendationConfig.COSEARCH_MIN_SCORE) return;

        upsertDynamicTag(videoId, keyword.trim(), "co_search",
                clamp(score, RecommendationConfig.COSEARCH_W_MIN, RecommendationConfig.COSEARCH_W_MAX),
                score,
                1);
    }

    /** 记录连续观看对 (共观扩散信号) */
    public void onConsecutiveWatch(String sessionId, Long previousVideoId, Long currentVideoId,
                                    double prevCompletion, double currCompletion) {
        if (sessionId == null || previousVideoId == null || currentVideoId == null) return;
        if (previousVideoId.equals(currentVideoId)) return;
        // 两边完播率都 > 50% 才算有效共观
        if (prevCompletion < RecommendationConfig.COWATCH_MIN_COMPLETION
                || currCompletion < RecommendationConfig.COWATCH_MIN_COMPLETION) return;

        coWatchPairs.computeIfAbsent(previousVideoId, k -> new ConcurrentHashMap<>());
        var inner = coWatchPairs.get(previousVideoId);
        inner.computeIfAbsent(currentVideoId, k -> new AtomicInteger(0)).incrementAndGet();

        int pairCount = inner.get(currentVideoId).get();
        if (pairCount >= COWATCH_THRESHOLD) {
            // 达到阈值, 即时扩散
            diffuseTagPair(previousVideoId, currentVideoId);
            inner.remove(currentVideoId);  // 扩散完清理, 避免重复
        }
    }

    /** 真实行为验证: 查询视频的观看/互动指标, 输出校准因子 */
    private double computeBehaviorValidation(Long videoId) {
        try {
            var metrics = watchHistoryMapper.computeVideoBehaviorMetrics(
                    videoId, LocalDateTime.now().minusDays(7));
            if (metrics == null || metrics.isEmpty()) return 1.0;

            double avgCompletion = toDoubleVal(metrics.get("avg_completion"),
                    RecommendationConfig.DEF_COMPLETION);
            double bounceRate = toDoubleVal(metrics.get("bounce_rate"),
                    RecommendationConfig.DEF_SCORE_MED);
            long viewCount = toLongVal(metrics.get("view_count"), 0);

            if (viewCount < RecommendationConfig.VAL_MIN_VIEWS) return 1.0;

            double factor = 1.0;

            if (avgCompletion > RecommendationConfig.VAL_HIGH_COMPLETION) factor += VAL_COMPLETION_BONUS;
            else if (avgCompletion < RecommendationConfig.VAL_LOW_COMPLETION) factor -= VAL_BOUNCE_PENALTY;

            if (bounceRate > RecommendationConfig.VAL_HIGH_BOUNCE) factor -= VAL_BOUNCE_PENALTY;

            // 查点赞率和评论率
            try {
                VideoContent vc = contentMapper.selectById(videoId);
                if (vc != null && viewCount > 0) {
                    // 用视频总互动数 / 观看数近似互动率 (实际应从 t_like / t_comment 统计)
                    // 这里用 quality_score 作为代理: 高质量视频通常标签准确
                    if (vc.getQualityScore() != null
                            && vc.getQualityScore() > RecommendationConfig.VAL_QUALITY_THRESHOLD) {
                        factor += VAL_LIKE_BONUS;
                    }
                }
            } catch (Exception ignored) {}

            return clamp(factor, RecommendationConfig.VAL_FACTOR_MIN, RecommendationConfig.VAL_FACTOR_MAX);
        } catch (Exception e) {
            return 1.0;
        }
    }

    /** 行为校准此视频: AI标签置信度 + quality_score 双重校准 */
    public void calibrateAiTagsForVideo(Long videoId) {
        double factor = computeBehaviorValidation(videoId);
        if (Math.abs(factor - 1.0) < RecommendationConfig.VAL_EPSILON) return;

        // 1) 更新 quality_score: AI 分与行为分 EMA 融合
        VideoContent vc = contentMapper.selectById(videoId);
        if (vc != null && vc.getQualityScore() != null) {
            double aiScore = vc.getQualityScore();
            double blended = clamp(
                    aiScore * RecommendationConfig.VAL_QUALITY_AI_W
                    + (aiScore * factor) * RecommendationConfig.VAL_QUALITY_BEHAVIOR_W,
                    RecommendationConfig.TAG_MIN_WEIGHT, 1.0);
            vc.setQualityScore(blended);
            contentMapper.updateById(vc);
        }

        // 2) 校准 AI 标签置信度
        List<VideoTag> aiTags = tagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getVideoId, videoId)
                        .eq(VideoTag::getSource, "ai"));

        for (VideoTag t : aiTags) {
            double origConf = t.getConfidence() != null ? t.getConfidence() : RecommendationConfig.DEF_TAG_CONF;
            double calibrated = clamp(origConf * factor, RecommendationConfig.VAL_CONF_FLOOR, 1.0);
            t.setConfidence(calibrated);
            t.setWeight(clamp((t.getWeight() != null ? t.getWeight() : RecommendationConfig.DEF_TAG_WEIGHT) * factor,
                    RecommendationConfig.TAG_MIN_WEIGHT, 1.0));
            tagMapper.updateById(t);
        }

        if (!aiTags.isEmpty()) {
            log.debug("行为校准: videoId={} factor={:.2f} 标签数={}", videoId, factor, aiTags.size());
        }
    }

    // ===================== 定时任务 (二期增强) =====================

    /** 每 15 分钟校准近期有行为的视频的 AI 标签 */
    @Scheduled(fixedDelay = 900_000)
    public void scheduledCalibration() {
        try {
            // 找最近 1 小时内有新观看记录的视频
            var recentMetrics = watchHistoryMapper.computeVideoBehaviorMetrics(
                    null, LocalDateTime.now().minusHours(1));
            // 这里用 null 做不了批量, 简化: 随机采样最近有行为的视频
            calibrateRecentVideos();
        } catch (Exception e) {
            log.warn("定时校准失败", e);
        }
    }

    /** 每 10 分钟做共观标签扩散 */
    @Scheduled(fixedDelay = 600_000)
    public void scheduledCowatchDiffusion() {
        Set<Long> videoIds = new HashSet<>(coWatchPairs.keySet());
        for (Long vidA : videoIds) {
            var inner = coWatchPairs.remove(vidA);
            if (inner == null || inner.isEmpty()) continue;
            for (var entry : inner.entrySet()) {
                Long vidB = entry.getKey();
                int count = entry.getValue().get();
                if (count >= COWATCH_THRESHOLD) {
                    diffuseTagPair(vidA, vidB);
                }
            }
        }
    }

    /** 每 15 分钟做热榜趋势匹配: 全局热搜词 → 匹配视频 → trend 标签 */
    @Scheduled(fixedDelay = 900_000)
    public void scheduledTrendMatching() {
        try {
            List<Map.Entry<String, Integer>> trendKeywords = fetchTrendKeywords(15);
            if (trendKeywords.isEmpty()) return;

            int maxHeat = trendKeywords.get(0).getValue();
            for (var entry : trendKeywords) {
                String kw = entry.getKey();
                int heat = entry.getValue();
                double weight = clamp(RecommendationConfig.TREND_W_BASE
                        + (double) heat / Math.max(1, maxHeat) * RecommendationConfig.TREND_W_RANGE,
                        RecommendationConfig.TREND_W_MIN, RecommendationConfig.TREND_W_MAX);
                double confidence = clamp(RecommendationConfig.TREND_C_BASE
                        + (double) heat / Math.max(1, maxHeat) * RecommendationConfig.TREND_C_RANGE,
                        RecommendationConfig.TREND_C_MIN, RecommendationConfig.TREND_C_MAX);

                try {
                    List<Video> matched = videoMapper.searchByKeyword(kw);
                    for (Video v : matched) {
                        upsertDynamicTag(v.getId(), kw, "trend", weight, confidence, 1);
                    }
                    if (!matched.isEmpty()) {
                        log.debug("趋势标签: kw={} heat={} matched={}", kw, heat, matched.size());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("趋势标签匹配失败", e);
        }
    }

    /** 从近7天用户搜索 + 热门视频描述中提取趋势关键词 */
    private List<Map.Entry<String, Integer>> fetchTrendKeywords(int limit) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        try {
            List<com.douyin.entity.UserContentProfile> profiles = profileMapper.selectList(
                    new LambdaQueryWrapper<com.douyin.entity.UserContentProfile>()
                            .ge(com.douyin.entity.UserContentProfile::getUpdateTime,
                                    LocalDateTime.now().minusDays(7))
                            .isNotNull(com.douyin.entity.UserContentProfile::getRecentSearchQueries)
                            .last("LIMIT " + RecommendationConfig.LIMIT_TREND_PROFILES));
            for (com.douyin.entity.UserContentProfile p : profiles) {
                String json = p.getRecentSearchQueries();
                if (json == null || json.isEmpty()) continue;
                try {
                    List<String> queries = objectMapper.readValue(json,
                            new TypeReference<List<String>>() {});
                    for (String q : queries) {
                        if (q != null && q.length() >= RecommendationConfig.SS_KW_MIN_LEN
                                && q.length() <= RecommendationConfig.SS_KW_MAX_LEN) {
                            freq.merge(q.trim(), 1, Integer::sum);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 热门视频描述补充
        try {
            List<Video> hotVideos = videoMapper.selectList(
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getStatus, "APPROVED")
                            .orderByDesc(Video::getLikeCount)
                            .last("LIMIT " + RecommendationConfig.LIMIT_TREND_HOT_VIDEOS));
            for (Video v : hotVideos) {
                if (v.getDesc() == null) continue;
                String desc = v.getDesc().trim();
                if (desc.length() >= RecommendationConfig.SS_DESC_MIN_LEN
                        && desc.length() <= RecommendationConfig.SS_DESC_MAX_LEN) {
                    freq.merge(desc, freq.getOrDefault(desc, 0) + RecommendationConfig.LIMIT_MULTI_LABEL,
                            Integer::sum);
                }
            }
        } catch (Exception ignored) {}

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    /** 扩散标签: 将 A 的高置信标签扩散给 B, 反之亦然 */
    private void diffuseTagPair(Long videoIdA, Long videoIdB) {
        List<VideoTag> tagsA = tagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getVideoId, videoIdA)
                        .gt(VideoTag::getConfidence, RecommendationConfig.TAG_DIFFUSE_MIN_CONF));
        List<VideoTag> tagsB = tagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getVideoId, videoIdB)
                        .gt(VideoTag::getConfidence, RecommendationConfig.TAG_DIFFUSE_MIN_CONF));

        // A → B: A 有但 B 没有的高置信标签
        Set<String> existingB = tagsB.stream().map(VideoTag::getTag).collect(Collectors.toSet());
        for (VideoTag t : tagsA) {
            if (!existingB.contains(t.getTag())) {
                double diffuseWeight = clamp(
                        (t.getConfidence() != null ? t.getConfidence() : RecommendationConfig.DEF_TAG_CONF)
                                * RecommendationConfig.TAG_DIFFUSE_W_DISCOUNT,
                        RecommendationConfig.TAG_DIFFUSE_W_MIN, RecommendationConfig.TAG_DIFFUSE_W_MAX);
                upsertDynamicTag(videoIdB, t.getTag(), "cowatch", diffuseWeight,
                        t.getConfidence() != null
                                ? t.getConfidence() * RecommendationConfig.TAG_DIFFUSE_C_DISCOUNT
                                : RecommendationConfig.DEF_TAG_CONF, 1);
            }
        }

        // B → A
        Set<String> existingA = tagsA.stream().map(VideoTag::getTag).collect(Collectors.toSet());
        for (VideoTag t : tagsB) {
            if (!existingA.contains(t.getTag())) {
                double diffuseWeight = clamp(
                        (t.getConfidence() != null ? t.getConfidence() : RecommendationConfig.DEF_TAG_CONF)
                                * RecommendationConfig.TAG_DIFFUSE_W_DISCOUNT,
                        RecommendationConfig.TAG_DIFFUSE_W_MIN, RecommendationConfig.TAG_DIFFUSE_W_MAX);
                upsertDynamicTag(videoIdA, t.getTag(), "cowatch", diffuseWeight,
                        t.getConfidence() != null
                                ? t.getConfidence() * RecommendationConfig.TAG_DIFFUSE_C_DISCOUNT
                                : RecommendationConfig.DEF_TAG_CONF, 1);
            }
        }

        log.debug("共观扩散: {} ↔ {} 标签A={} 标签B={}", videoIdA, videoIdB, tagsA.size(), tagsB.size());
    }

    /** 批量校准近期视频 */
    private void calibrateRecentVideos() {
        try {
            // 取最近 1 小时内有 AI 标签的视频
            List<VideoTag> recentAiTags = tagMapper.selectList(
                    new LambdaQueryWrapper<VideoTag>()
                            .eq(VideoTag::getSource, "ai")
                            .ge(VideoTag::getLastSignal,
                                    LocalDateTime.now().minusDays(RecommendationConfig.DAYS_CALIBRATION_SINCE))
                            .last("LIMIT " + RecommendationConfig.LIMIT_TAG_CALIBRATION));
            Set<Long> videoIds = recentAiTags.stream()
                    .map(VideoTag::getVideoId).collect(Collectors.toSet());
            for (Long vid : videoIds) {
                try {
                    calibrateAiTagsForVideo(vid);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    // ===================== 定时 flush =====================

    /** 每 5 分钟将累积的信号批量写入数据库 */
    @Scheduled(fixedDelay = 300_000)
    public void flushAll() {
        // 评论信号
        Set<Long> videoIds = new HashSet<>(commentSignals.keySet());
        for (Long vid : videoIds) {
            try {
                flushCommentTags(vid);
            } catch (Exception e) {
                log.warn("评论标签 flush 失败: videoId={}", vid, e);
            }
        }

        // 搜索关联信号
        Set<Long> searchVideoIds = new HashSet<>(searchWatchSignals.keySet());
        for (Long vid : searchVideoIds) {
            try {
                flushSearchWatchTags(vid);
            } catch (Exception e) {
                log.warn("搜索关联标签 flush 失败: videoId={}", vid, e);
            }
        }

        // 衰减老旧标签
        try {
            decayStaleTags();
        } catch (Exception e) {
            log.warn("标签衰减失败", e);
        }
    }

    /** 内容相似度标签继承: 新视频从相似老视频继承行为标签, 保留原始来源链 */
    public void inheritTag(Long videoId, String tag, double confidence, String originalSource) {
        double weight = clamp(confidence * RecommendationConfig.TAG_INHERIT_W_DISCOUNT,
                RecommendationConfig.TAG_MIN_WEIGHT, 0.5);
        double conf = clamp(confidence * RecommendationConfig.TAG_INHERIT_C_DISCOUNT, 0.1, 0.7);
        String source = "inherit:" + (originalSource != null && !originalSource.isEmpty() ? originalSource : "unknown");
        upsertDynamicTag(videoId, tag, source, weight, conf, 1);
    }

    /** 批量保存 AI 提取的初始标签 (特征提取完成后调用) */
    public void saveInitialTags(Video video, Map<String, Object> features) {
        // 由 ContentFeatureService 调用，从特征中提取所有标签
        Long videoId = video.getId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) features.get("categories");
        if (categories != null) {
            for (Map<String, Object> cat : categories) {
                String label = (String) cat.get("label");
                Object confObj = cat.get("confidence");
                double confidence = confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.5;
                upsertDynamicTag(videoId, label, "ai", confidence, confidence, 0);
            }
        }

        // 场景标签
        @SuppressWarnings("unchecked")
        List<String> scenes = (List<String>) features.get("scene_tags");
        if (scenes != null) {
            for (String s : scenes) {
                upsertDynamicTag(videoId, s, "ai",
                        RecommendationConfig.KI_SCENE_W, RecommendationConfig.KI_SCENE_C, 0);
            }
        }

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) features.get("keywords");
        if (keywords != null) {
            for (String kw : keywords) {
                upsertDynamicTag(videoId, kw, "ai",
                        RecommendationConfig.KI_KEYWORD_W, RecommendationConfig.KI_KEYWORD_C, 0);
            }
        }

        @SuppressWarnings("unchecked")
        List<String> attrs = (List<String>) features.get("content_attributes");
        if (attrs != null) {
            for (String a : attrs) {
                upsertDynamicTag(videoId, a, "ai",
                        RecommendationConfig.KI_ATTR_W, RecommendationConfig.KI_ATTR_C, 0);
            }
        }
    }

    /** 获取视频的所有标签 (含多源) */
    public List<Map<String, Object>> getTags(Long videoId) {
        List<VideoTag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getVideoId, videoId)
                        .orderByDesc(VideoTag::getWeight));

        return tags.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", t.getTag());
            m.put("source", t.getSource());
            m.put("weight", t.getWeight());
            m.put("confidence", t.getConfidence());
            m.put("signalCount", t.getSignalCount());
            m.put("lastSignal", t.getLastSignal());
            return m;
        }).collect(Collectors.toList());
    }

    // ===================== 内部: 标签持久化 =====================

    /** 核心: 写入/更新标签, 多源信号加权融合 */
    private void upsertDynamicTag(Long videoId, String tag, String source,
                                   double weight, double confidence, int signalCount) {
        if (tag.length() > RecommendationConfig.TAG_MAX_LEN) tag = tag.substring(0, RecommendationConfig.TAG_MAX_LEN);

        VideoTag existing = tagMapper.selectOne(
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getVideoId, videoId)
                        .eq(VideoTag::getTag, tag));

        double aiBase = 0;
        int existingSignal = 0;

        if (existing != null) {
            // 融合: 已有标签按源加权
            aiBase = existing.getAiBase() != null ? existing.getAiBase() : 0;
            existingSignal = existing.getSignalCount() != null ? existing.getSignalCount() : 0;

            // 多源融合公式
            double mergedWeight = mergeWeights(existing, source, weight, signalCount);
            double mergedConfidence = Math.max(
                    existing.getConfidence() != null ? existing.getConfidence() : 0,
                    confidence);

            existing.setWeight(mergedWeight);
            existing.setConfidence(mergedConfidence);
            existing.setSignalCount(existingSignal + signalCount);
            existing.setLastSignal(LocalDateTime.now());
            if (existing.getSource() != null && !existing.getSource().contains(source)) {
                existing.setSource(existing.getSource() + "," + source);
            } else if (existing.getSource() == null) {
                existing.setSource(source);
            }
            tagMapper.updateById(existing);
        } else {
            VideoTag newTag = new VideoTag();
            newTag.setVideoId(videoId);
            newTag.setTag(tag);
            newTag.setSource(source);
            newTag.setWeight(weight);
            newTag.setConfidence(confidence);
            newTag.setSignalCount(signalCount);
            newTag.setAiBase(aiBase);
            newTag.setLastSignal(LocalDateTime.now());
            try {
                tagMapper.insert(newTag);
            } catch (Exception e) {
                // duplicate key → 并发写入, 忽略
            }
        }
    }

    /** 多源权重融合 */
    private double mergeWeights(VideoTag existing, String newSource,
                                 double newWeight, int newSignalCount) {
        double oldWeight = existing.getWeight() != null ? existing.getWeight() : 0;
        String oldSource = existing.getSource() != null ? existing.getSource() : "";

        // 不同源的权重按比例融合
        if (!oldSource.contains(newSource)) {
            // 新源 → 加权平均
            double totalSignal = (existing.getSignalCount() != null ? existing.getSignalCount() : 0)
                    + newSignalCount;
            double oldRatio = existing.getSignalCount() != null
                    ? existing.getSignalCount() / Math.max(1, totalSignal) : 0.5;
            return oldWeight * oldRatio + newWeight * (1 - oldRatio);
        } else {
            // 同源 → EMA 更新
            return oldWeight * RecommendationConfig.MERGE_SAME_SRC_OLD_W
                    + newWeight * RecommendationConfig.MERGE_SAME_SRC_NEW_W;
        }
    }

    // ===================== 内部: flush 逻辑 =====================

    private void flushCommentTags(Long videoId) {
        var signalMap = commentSignals.remove(videoId);
        if (signalMap == null || signalMap.isEmpty()) return;

        int totalComments = signalMap.values().stream().mapToInt(AtomicInteger::get).sum();

        for (var entry : signalMap.entrySet()) {
            int count = entry.getValue().get();
            if (count >= COMMENT_THRESHOLD) {
                String keyword = entry.getKey();
                double weight = clamp(count / (double) Math.max(1, totalComments) * RecommendationConfig.COMMENT_W_SCALE,
                        RecommendationConfig.COMMENT_W_MIN, RecommendationConfig.COMMENT_W_MAX);
                double confidence = clamp(RecommendationConfig.COMMENT_C_BASE + count * RecommendationConfig.COMMENT_C_PER,
                        RecommendationConfig.COMMENT_C_MIN, RecommendationConfig.COMMENT_C_MAX);
                upsertDynamicTag(videoId, keyword, "comment", weight, confidence, count);
                log.debug("评论标签: videoId={} tag={} count={}", videoId, keyword, count);
            }
        }
    }

    private void flushSearchWatchTags(Long videoId) {
        var signalMap = searchWatchSignals.remove(videoId);
        if (signalMap == null || signalMap.isEmpty()) return;

        for (var entry : signalMap.entrySet()) {
            SearchWatchAccumulator acc = entry.getValue();
            if (acc.count >= SEARCH_THRESHOLD) {
                String keyword = entry.getKey();
                // 搜索标签权重: 基于完播率和用户数
                double avgCompletion = acc.totalCompletion / acc.count;
                double weight = clamp(acc.count * RecommendationConfig.SEARCH_W_PER * avgCompletion,
                        RecommendationConfig.SEARCH_W_MIN, RecommendationConfig.SEARCH_W_MAX);
                double confidence = clamp(RecommendationConfig.SEARCH_C_BASE + acc.count * RecommendationConfig.SEARCH_C_PER,
                        RecommendationConfig.SEARCH_C_MIN, RecommendationConfig.SEARCH_C_MAX);
                upsertDynamicTag(videoId, keyword, "search", weight, confidence, acc.count);
                log.debug("搜索关联标签: videoId={} tag={} count={}", videoId, keyword, acc.count);
            }
        }
    }

    // ===================== 内部: 标签衰减 =====================

    /** 7天无新信号的标签权重衰减 */
    private void decayStaleTags() {
        LocalDateTime stalePoint = LocalDateTime.now().minusDays(RecommendationConfig.DAYS_TAG_STALE);
        List<VideoTag> staleTags = tagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>()
                        .lt(VideoTag::getLastSignal, stalePoint)
                        .gt(VideoTag::getWeight, RecommendationConfig.TAG_STALE_CONSIDER_W));

        for (VideoTag t : staleTags) {
            double newWeight = (t.getWeight() != null ? t.getWeight() : RecommendationConfig.TAG_DEFAULT_STALE_W) * DECAY_FACTOR;
            String src = t.getSource();
            if (newWeight < MIN_WEIGHT && !"ai".equals(src) && (src == null || !src.startsWith("inherit"))) {
                // 非 AI/继承标签, 权重太低 → 删除
                tagMapper.deleteById(t.getId());
                log.debug("标签消亡: videoId={} tag={}", t.getVideoId(), t.getTag());
            } else {
                t.setWeight(newWeight);
                t.setLastSignal(LocalDateTime.now());  // 更新最后信号时间避免反复衰减
                tagMapper.updateById(t);
            }
        }
    }

    // ===================== 内部: 中文短语提取 =====================

    /** 从文本中提取 2-4 字高频短语 (轻量替代 jieba) */
    private List<String> extractPhrases(String text) {
        if (text == null || text.length() < 2) return List.of();

        // 移除标点和空白
        String clean = text.replaceAll("[\\p{P}\\p{S}\\s]+", "");
        if (clean.length() < 2) return List.of();

        Set<String> seen = new LinkedHashSet<>();
        // 2字词
        for (int i = 0; i < clean.length() - 1; i++) {
            String w = clean.substring(i, Math.min(i + 4, clean.length()));
            if (w.length() >= 2 && !isStopWord(w)) {
                seen.add(w);
            }
        }
        // 优先保留较长的短语
        return seen.stream()
                .filter(w -> w.length() >= RecommendationConfig.KEYWORD_MIN_LEN)
                .sorted((a, b) -> b.length() - a.length())
                .limit(RecommendationConfig.LIMIT_MAX_PHRASES)
                .collect(Collectors.toList());
    }

    private boolean isStopWord(String w) {
        return w.matches("[0-9０-９]+")   // 纯数字
                || w.matches("[a-zA-Z]+")  // 纯英文
                || w.contains("哈") || w.contains("啦") || w.contains("吧")
                || w.contains("呢") || w.contains("吗") || w.contains("哦")
                || w.contains("嗯") || w.contains("啊") || w.contains("呀")
                || w.equals("哈哈哈") || w.equals("嘿嘿") || w.equals("呵呵")
                || w.equals("就是") || w.equals("真的") || w.equals("觉得")
                || w.equals("这个") || w.equals("那个") || w.equals("什么")
                || w.equals("怎么") || w.equals("不是") || w.equals("可以")
                || w.equals("已经") || w.equals("因为") || w.equals("所以")
                || w.equals("但是") || w.equals("然后") || w.equals("如果")
                || w.equals("应该") || w.equals("可能") || w.equals("感觉")
                || w.equals("知道") || w.equals("喜欢") || w.equals("比较")
                || w.equals("一下") || w.equals("一点") || w.equals("一些")
                || w.equals("很多") || w.equals("非常") || w.equals("确实")
                || w.equals("没有") || w.equals("自己") || w.equals("大家");
    }

    // ===================== 工具 =====================

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double toDoubleVal(Object obj, double defaultVal) {
        if (obj instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    private long toLongVal(Object obj, long defaultVal) {
        if (obj instanceof Number n) return n.longValue();
        return defaultVal;
    }

    /** 搜索→观看信号累加器 */
    private static class SearchWatchAccumulator {
        int count = 0;
        double totalCompletion = 0;

        void add(double completion) {
            count++;
            totalCompletion += completion;
        }
    }
}
