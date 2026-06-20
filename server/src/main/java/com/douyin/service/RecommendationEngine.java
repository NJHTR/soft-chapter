package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 推荐引擎: 多路召回 → 多信号排序 → 多样性重排
 *
 * 召回通道: 内容召回 / 协同过滤 / 社交召回 / 热门召回 / 探索召回 / 创作者存量召回
 * 排序信号 (13 维): 内容匹配 / 视频质量 / 创作者亲和力 / 行为匹配 /
 *          社交关系 / 个人历史 / 热度趋势 / 新鲜度 / 用户类型加成 /
 *          探索激励 / 观看历史惩罚 / 社会证明 / 创作者互动
 *
 * 关键优化:
 * - 已点赞/收藏的视频高分惩罚（非硬过滤）：点赞 0.95，收藏 0.80
 * - 观看历史惩罚：看过的视频降权，完播未互动大幅降权
 * - 社会证明带时间衰减：旧视频的高赞数不会永久压制新视频 (30天半衰期)
 * - 创作者互动信号：你点赞/收藏过的作者发新视频自动提权
 * - 创作者存量召回：关注/互动过的作者中未刷到的优质旧视频也有曝光机会
 */
@Slf4j
@Service
public class RecommendationEngine {

    private static final int RECALL_PER_CHANNEL = 50;
    private static final int CANDIDATE_POOL_SIZE = 200;

    // 排序权重 (总和 = 1.0)  — 内容相关是推荐的第一驱动力
    private static final double CONTENT_WEIGHT = 0.19;
    private static final double QUALITY_WEIGHT = 0.07;
    private static final double CREATOR_AFFINITY_WEIGHT = 0.07;
    private static final double BEHAVIORAL_WEIGHT = 0.06;
    private static final double SOCIAL_WEIGHT = 0.05;
    private static final double PERSONAL_HISTORY_WEIGHT = 0.05;
    private static final double POPULARITY_WEIGHT = 0.04;
    private static final double FRESHNESS_WEIGHT = 0.12;
    private static final double USER_TYPE_WEIGHT = 0.04;
    private static final double EXPLORATION_WEIGHT = 0.03;
    private static final double WATCH_HISTORY_PENALTY_WEIGHT = 0.10;
    private static final double SOCIAL_PROOF_WEIGHT = 0.08;
    private static final double CREATOR_INTERACTION_WEIGHT = 0.10;

    private static final long FRESHNESS_HALF_LIFE_HOURS = 48;
    private static final long SOCIAL_PROOF_HALF_LIFE_HOURS = 720; // 30天 → 旧视频社交证明逐步衰减
    private static final double EXPLORE_INJECT_RATE = 0.12;
    private static final int MAX_PER_AUTHOR = 2;
    private static final int MAX_PER_CATEGORY = 3;
    private static final int MAX_PER_TYPE = 3;
    private volatile List<String> cachedTrendingKeywords = List.of();
    private volatile long trendingKeywordsCacheTime = 0;
    private static final long TRENDING_CACHE_MS = 300_000; // 5分钟缓存

    private final VideoMapper videoMapper;
    private final VideoContentMapper contentMapper;
    private final VideoExposureMapper exposureMapper;
    private final UserContentProfileMapper profileMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoCollectMapper collectMapper;
    private final UserMapper userMapper;
    private final UserProfileService profileService;
    private final ObjectMapper objectMapper;

    public RecommendationEngine(VideoMapper videoMapper, VideoContentMapper contentMapper,
                                VideoExposureMapper exposureMapper, UserContentProfileMapper profileMapper,
                                LikeMapper likeMapper, FollowMapper followMapper,
                                WatchHistoryMapper watchHistoryMapper,
                                VideoCollectMapper collectMapper, UserMapper userMapper,
                                UserProfileService profileService) {
        this.videoMapper = videoMapper;
        this.contentMapper = contentMapper;
        this.exposureMapper = exposureMapper;
        this.profileMapper = profileMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.collectMapper = collectMapper;
        this.userMapper = userMapper;
        this.profileService = profileService;
        this.objectMapper = new ObjectMapper();
    }

    /** 主入口: 为用户推荐视频 */
    public List<Long> recommend(Long userId, int pageSize, Double minDuration) {
        // 0. 获取已互动视频 ID + 互动作者集合
        int recentCount = 0, likedCount = 0, collectedCount = 0, watchedCount = 0;

        // 硬过滤：24h 内曝光过的
        Set<Long> excludeIds = getRecentExposures(userId);
        recentCount = excludeIds.size();

        // 软惩罚：已点赞/收藏的视频不硬排除，但给时间衰减惩罚
        Map<Long, LocalDateTime> likedMap = getLikedVideoMap(userId);
        likedCount = likedMap.size();

        Map<Long, LocalDateTime> collectedMap = getCollectedVideoMap(userId);
        collectedCount = collectedMap.size();

        // 硬过滤：24h 内完整观看过的 (保护用户不重复刷到刚看完的)
        Set<Long> recentWatchedIds = getFullyWatchedVideoIds(userId, 1);
        watchedCount = recentWatchedIds.size();
        excludeIds.addAll(recentWatchedIds);

        // 软惩罚：1~7 天内完整观看过的 (不硬排除，但降权)
        Set<Long> olderWatchedIds = getFullyWatchedVideoIds(userId, 7);
        olderWatchedIds.removeAll(recentWatchedIds);

        // 互动过的作者ID集合 (点赞/收藏过其内容 → 不是自己的内容)
        Set<Long> interactedAuthorIds = getInteractedAuthorIds(userId, likedMap, collectedMap);

        UserContentProfile profile = profileMapper.selectById(userId);

        // 1. 多路召回 (6 路)
        List<Long> cfCandidates = collaborativeRecall(userId, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> socialCandidates = socialRecall(userId, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> contentCandidates = contentRecall(profile, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> hotCandidates = hotRecall(excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> exploreCandidates = exploreRecall(profile, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> backlogCandidates = creatorBacklogRecall(userId, excludeIds, interactedAuthorIds,
                RECALL_PER_CHANNEL, minDuration);

        // 2. 合并去重 — 创作者存量召回优先排在前面
        Set<Long> candidateSet = new LinkedHashSet<>();
        candidateSet.addAll(backlogCandidates);
        candidateSet.addAll(contentCandidates);
        candidateSet.addAll(cfCandidates);
        candidateSet.addAll(socialCandidates);
        candidateSet.addAll(hotCandidates);
        candidateSet.addAll(exploreCandidates);
        candidateSet.removeAll(excludeIds);

        if (candidateSet.size() < pageSize) {
            List<Video> fallback = videoMapper.findRecallCandidates(
                    new ArrayList<>(excludeIds), minDuration, RECALL_PER_CHANNEL);
            fallback.forEach(v -> candidateSet.add(v.getId()));
        }

        List<Long> candidates = new ArrayList<>(candidateSet);
        if (candidates.isEmpty()) return List.of();

        // 3. 批量加载候选视频 + 内容特征 + 观看历史 + 作者粉丝数
        List<Video> videos = videoMapper.selectBatchIds(
                candidates.subList(0, Math.min(candidates.size(), CANDIDATE_POOL_SIZE)));
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());
        Map<Long, WatchHistory> watchMap = loadWatchMap(userId,
                videos.stream().map(Video::getId).toList());
        Map<Long, Long> authorFollowerMap = loadAuthorFollowerMap(
                videos.stream().map(Video::getAuthorUserId).filter(Objects::nonNull).distinct().toList());

        // 近期互动数据 (用于动态时效判断)
        Map<Long, RecentEngagement> recentEngMap = loadRecentEngagement(
                videos.stream().map(Video::getId).toList());

        // 全站完播率 (用于质量信号)
        Map<Long, Double> avgCompletionMap = loadAvgCompletion(
                videos.stream().map(Video::getId).toList());

        // 4. 多信号打分 (13 维)
        List<ScoredVideo> scored = scoreVideos(userId, videos, contentMap, watchMap,
                authorFollowerMap, profile, likedMap, collectedMap, interactedAuthorIds,
                recentEngMap, avgCompletionMap, olderWatchedIds);

        // 构建视频年龄映射 (小时)
        Map<Long, Long> videoAgeHours = videos.stream()
                .filter(v -> v.getCreateTime() != null)
                .collect(Collectors.toMap(Video::getId,
                        v -> ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now())));

        // 构建视频类型映射 (用于内容类型多样性)
        Map<Long, String> videoTypeMap = videos.stream()
                .filter(v -> v.getType() != null)
                .collect(Collectors.toMap(Video::getId, Video::getType, (a, b) -> a));

        // 5. 多样性重排
        List<Long> result = reRank(scored, contentMap, exploreCandidates, pageSize, videoAgeHours, videoTypeMap);

        // 6. 记录曝光
        recordExposuresBatch(userId, result);

        log.info("推荐完成: userId={} candidates={} exclude[recent={} liked={} collected={} watched={}] interactedAuthors={} result={}",
                userId, candidates.size(),
                recentCount, likedCount, collectedCount, watchedCount,
                interactedAuthorIds.size(), result.size());

        return result;
    }

    // ===================== 已互动内容映射 =====================

    /** 用户已点赞的视频 → 点赞时间 (用于时间衰减惩罚) */
    private Map<Long, LocalDateTime> getLikedVideoMap(Long userId) {
        return likeMapper.selectList(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .select(Like::getVideoId, Like::getCreateTime))
                .stream()
                .filter(l -> l.getCreateTime() != null)
                .collect(Collectors.toMap(Like::getVideoId, Like::getCreateTime, (a, b) -> a));
    }

    /** 用户已收藏的视频 → 收藏时间 (用于时间衰减惩罚) */
    private Map<Long, LocalDateTime> getCollectedVideoMap(Long userId) {
        return collectMapper.selectList(new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId)
                .select(VideoCollect::getVideoId, VideoCollect::getCreateTime))
                .stream()
                .filter(c -> c.getCreateTime() != null)
                .collect(Collectors.toMap(VideoCollect::getVideoId, VideoCollect::getCreateTime, (a, b) -> a));
    }

    /** 最近 N 天内完整观看过的视频 ID 集合 */
    private Set<Long> getFullyWatchedVideoIds(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .ge(WatchHistory::getCreateTime, since)
                .eq(WatchHistory::getFinished, 1))
                .stream().map(WatchHistory::getVideoId)
                .collect(Collectors.toSet());
    }

    /** 用户互动过的作者 ID 集合 (通过点赞/收藏的视频反查作者) */
    private Set<Long> getInteractedAuthorIds(Long userId, Map<Long, LocalDateTime> likedMap,
                                              Map<Long, LocalDateTime> collectedMap) {
        Set<Long> videoIds = new HashSet<>();
        videoIds.addAll(likedMap.keySet());
        videoIds.addAll(collectedMap.keySet());

        if (videoIds.isEmpty()) return Set.of();

        return videoMapper.selectBatchIds(videoIds).stream()
                .map(Video::getAuthorUserId)
                .filter(Objects::nonNull)
                .filter(aid -> !aid.equals(userId))
                .collect(Collectors.toSet());
    }

    // ===================== 批量加载辅助数据 =====================

    /** 批量加载观看历史 (userId → candidateVideoId → WatchHistory) */
    private Map<Long, WatchHistory> loadWatchMap(Long userId, List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        // 取最近一次对该视频的观看记录
        return watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .in(WatchHistory::getVideoId, videoIds)
                .orderByDesc(WatchHistory::getCreateTime))
                .stream()
                .collect(Collectors.toMap(
                        WatchHistory::getVideoId, w -> w,
                        (existing, replacement) -> existing)); // 保留第一条 (最新)
    }

    /** 批量加载作者粉丝数 */
    private Map<Long, Long> loadAuthorFollowerMap(List<Long> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(authorIds).stream()
                .filter(u -> u.getFollowerCount() != null)
                .collect(Collectors.toMap(User::getUid, User::getFollowerCount));
    }

    /** 批量加载近期互动数据: 每个候选视频近7天的点赞数/观看人次/曝光次数 */
    private Map<Long, RecentEngagement> loadRecentEngagement(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        Map<Long, Integer> recentLikes = new HashMap<>();
        try {
            likeMapper.countRecentLikes(videoIds, since).forEach(row -> {
                Long vid = Long.valueOf(row.get("video_id").toString());
                Integer cnt = Integer.valueOf(row.get("cnt").toString());
                recentLikes.put(vid, cnt);
            });
        } catch (Exception ignored) {}

        Map<Long, Integer> recentWatches = new HashMap<>();
        try {
            watchHistoryMapper.countRecentWatches(videoIds, since).forEach(row -> {
                Long vid = Long.valueOf(row.get("video_id").toString());
                Integer cnt = Integer.valueOf(row.get("cnt").toString());
                recentWatches.put(vid, cnt);
            });
        } catch (Exception ignored) {}

        // 组装
        Map<Long, RecentEngagement> result = new HashMap<>();
        for (Long vid : videoIds) {
            result.put(vid, new RecentEngagement(
                    recentLikes.getOrDefault(vid, 0),
                    recentWatches.getOrDefault(vid, 0)));
        }
        return result;
    }

    /** 批量加载全站平均完播率 */
    private Map<Long, Double> loadAvgCompletion(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        try {
            return watchHistoryMapper.avgCompletionRate(videoIds).stream()
                    .collect(Collectors.toMap(
                            row -> Long.valueOf(row.get("video_id").toString()),
                            row -> Double.valueOf(row.get("avg_comp").toString()),
                            (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 近期互动数据 */
    private record RecentEngagement(int recentLikes, int recentWatches) {
        double pulse() {
            if (recentLikes == 0 && recentWatches == 0) return 0.0;
            double likePulse = Math.min(1.0, recentLikes / 50.0);
            double watchPulse = Math.min(1.0, recentWatches / 200.0);
            return likePulse * 0.6 + watchPulse * 0.4;
        }

        /** 自适应活跃判断: 体量大的视频需要更多近期互动才算"活跃" */
        boolean isActive() {
            return recentLikes >= 1 && recentWatches >= 3;
        }
    }

    // ===================== 多路召回 =====================

    /** 内容召回: 基于用户品类偏好 + 内容向量相似度 */
    private List<Long> contentRecall(UserContentProfile profile, Set<Long> excludeIds,
                                      int limit, Double minDuration) {
        List<Video> videos = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, limit * 2);
        if (profile == null || profile.getContentVector() == null || videos.isEmpty()) {
            return videos.stream().map(Video::getId).limit(limit).toList();
        }

        List<Double> userVec = parseVector(profile.getContentVector());
        if (userVec == null) return videos.stream().map(Video::getId).limit(limit).toList();

        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Map<Long, VideoContent> contentMap = loadContentMap(videoIds);

        return videos.stream()
                .map(v -> {
                    double sim = cosineSim(userVec, contentMap.get(v.getId()));
                    double catBonus = categoryBonus(profile, contentMap.get(v.getId()));
                    return new CandidateScore(v.getId(), sim + catBonus);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed())
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    /** 协同过滤召回: Item-based CF —— 共同被赞的视频 */
    private List<Long> collaborativeRecall(Long userId, Set<Long> excludeIds, int limit, Double minDuration) {
        List<Long> likedIds = likeMapper.findRecentLikedVideoIds(userId, 50);
        if (likedIds.isEmpty()) return List.of();

        List<Map<String, Object>> rows = likeMapper.findCoLikedVideoIds(
                likedIds, new ArrayList<>(excludeIds), limit * 3);
        if (rows.isEmpty()) return List.of();

        // 验证视频有效性: 过滤已删除/非公开/时长不足的视频
        List<Long> candidateIds = rows.stream()
                .map(r -> Long.valueOf(r.get("video_id").toString()))
                .toList();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getId, candidateIds)
                .eq(Video::getStatus, "APPROVED")
                .in(Video::getType, List.of("recommend-video", "image", "text"));
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        wrapper.last("LIMIT " + limit);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 社交召回: 关注作者 + 最近互动作者的新视频 */
    private List<Long> socialRecall(Long userId, Set<Long> excludeIds, int limit, Double minDuration) {
        Set<Long> authorIds = new LinkedHashSet<>();

        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId).last("LIMIT 100"));
        follows.forEach(f -> authorIds.add(f.getFollowId()));

        List<Long> recentAuthors = videoMapper.findRecentAuthorIds(userId, 20);
        authorIds.addAll(recentAuthors);

        if (authorIds.isEmpty()) return List.of();

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, authorIds)
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .last("LIMIT " + limit);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 热门召回: 高互动量 + 高质量分 */
    private List<Long> hotRecall(Set<Long> excludeIds, int limit, Double minDuration) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        wrapper.last("LIMIT " + limit);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 探索召回: 用户未充分接触的品类 + 随机高质量 */
    private List<Long> exploreRecall(UserContentProfile profile, Set<Long> excludeIds,
                                      int limit, Double minDuration) {
        List<Video> videos = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, limit * 3);

        if (profile == null || profile.getCategoryWeights() == null) {
            Collections.shuffle(videos, ThreadLocalRandom.current());
            return videos.stream().map(Video::getId).limit(limit).toList();
        }

        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());

        return videos.stream()
                .map(v -> {
                    VideoContent vc = contentMap.get(v.getId());
                    String cat = vc != null ? vc.getTextCategory() : null;
                    double exploreScore = cat != null ? (1.0 - catWeights.getOrDefault(cat, 0.0)) : 0.5;
                    return new CandidateScore(v.getId(),
                            exploreScore + ThreadLocalRandom.current().nextDouble() * 0.3);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed())
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    /**
     * 创作者存量召回: 关注/互动过的作者中，用户未刷到过的优质旧视频。
     * 解决"关注的人发了优质内容但我没刷到"的问题。
     */
    private List<Long> creatorBacklogRecall(Long userId, Set<Long> excludeIds,
                                             Set<Long> interactedAuthorIds,
                                             int limit, Double minDuration) {
        Set<Long> authorIds = new LinkedHashSet<>();
        // 关注作者
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId).last("LIMIT 100"));
        follows.forEach(f -> authorIds.add(f.getFollowId()));
        // 互动作者
        authorIds.addAll(interactedAuthorIds);

        if (authorIds.isEmpty()) return List.of();

        // 取这些作者的旧视频（7天前到30天内），按质量+互动量排序
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, authorIds)
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .le(Video::getCreateTime, LocalDateTime.now().minusDays(1))  // 至少1天前发布 (新视频走社交召回)
                .ge(Video::getCreateTime, LocalDateTime.now().minusDays(90)) // 不超过90天
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .last("LIMIT " + limit);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    // ===================== 多信号打分 (13 维) =====================

    private List<ScoredVideo> scoreVideos(Long userId, List<Video> videos,
                                           Map<Long, VideoContent> contentMap,
                                           Map<Long, WatchHistory> watchMap,
                                           Map<Long, Long> authorFollowerMap,
                                           UserContentProfile profile,
                                           Map<Long, LocalDateTime> likedMap,
                                           Map<Long, LocalDateTime> collectedMap,
                                           Set<Long> interactedAuthorIds,
                                           Map<Long, RecentEngagement> recentEngMap,
                                           Map<Long, Double> avgCompletionMap,
                                           Set<Long> olderWatchedIds) {
        if (profile == null) profile = profileService.getOrCreate(userId);

        List<Double> userVec = parseVector(profile.getContentVector());
        List<Double> userVecShort = parseVector(profile.getShortTermVector());
        Map<String, Double> creatorAffinity = parseCreatorAffinityMap(profile.getCreatorAffinity());
        List<String> recentSearches = parseRecentSearches(profile.getRecentSearchQueries());
        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        String userType = profile.getUserType() != null ? profile.getUserType() : "balanced";
        Map<Long, Double> authorAffinityCache = new HashMap<>();
        Set<Long> followedSet = getFollowedSet(userId);

        // 品类反馈: 一次查询完成正反馈+负反馈
        CategoryFeedback catFeedback = calcCategoryFeedback(userId);
        Map<String, Double> catSkipPenalties = catFeedback.skipPenalties();
        Map<String, Double> catLikeBoosts = catFeedback.likeBoosts();
        // 全局搜索趋势关键词
        List<String> trendingKeywords = getTrendingKeywords(10);

        List<ScoredVideo> scored = new ArrayList<>(videos.size());
        for (Video v : videos) {
            Long vid = v.getId();
            VideoContent vc = contentMap.get(vid);
            WatchHistory wh = watchMap.get(vid);
            Long authorFollowerCount = authorFollowerMap.getOrDefault(v.getAuthorUserId(), 0L);

            double contentMatch = calcContentMatch(userVec, userVecShort, vc);
            double avgComp = avgCompletionMap.getOrDefault(vid, 0.5);
            double qualityScore = calcQualityScore(v, vc, authorFollowerCount, avgComp);
            double creatorAff = calcCreatorAffinity(v.getAuthorUserId(), creatorAffinity);
            double behavioral = calcBehavioralMatch(v, vc, recentSearches, catWeights, userType,
                    trendingKeywords);
            double socialScore = calcSocialScore(userId, v, authorAffinityCache, followedSet);
            double personalHist = calcPersonalHistory(userId, v, vc, profile);
            double popularity = calcPopularityTrend(v);
            RecentEngagement re = recentEngMap.getOrDefault(vid, new RecentEngagement(0, 0));
            double freshness = calcFreshness(v, re);
            double userTypeBonus = calcUserTypeBonus(userType, v, vc, profile);
            double exploration = calcExplorationBonus(v, vc, catWeights, profile);
            double watchPenalty = calcWatchHistoryPenalty(wh, v, profile);
            double socialProof = calcSocialProof(v, authorFollowerCount, re);
            double creatorInteraction = calcCreatorInteraction(v, followedSet, interactedAuthorIds);

            // 互动惩罚 (时间衰减): 刚赞/收藏的少推
            double interactionPenalty = calcInteractionPenalty(vid, likedMap, collectedMap);

            // 完播软惩罚 (1~7 天内完整看过 → 降权但不禁)
            double watchedPenalty = olderWatchedIds.contains(vid) ? 0.15 : 0.0;

            // 品类负反馈: 连续快划过的品类降权
            double categoryPenalty = 0.0;
            if (vc != null && vc.getTextCategory() != null) {
                categoryPenalty = catSkipPenalties.getOrDefault(vc.getTextCategory(), 0.0);
            }

            // 品类正反馈: 用户完整看完的品类提权
            double categoryBoost = 0.0;
            if (vc != null && vc.getTextCategory() != null) {
                categoryBoost = catLikeBoosts.getOrDefault(vc.getTextCategory(), 0.0);
            }

            double score = CONTENT_WEIGHT * contentMatch
                    + QUALITY_WEIGHT * qualityScore
                    + CREATOR_AFFINITY_WEIGHT * creatorAff
                    + BEHAVIORAL_WEIGHT * behavioral
                    + SOCIAL_WEIGHT * socialScore
                    + PERSONAL_HISTORY_WEIGHT * personalHist
                    + POPULARITY_WEIGHT * popularity
                    + FRESHNESS_WEIGHT * freshness
                    + USER_TYPE_WEIGHT * userTypeBonus
                    + EXPLORATION_WEIGHT * exploration
                    - WATCH_HISTORY_PENALTY_WEIGHT * watchPenalty
                    + SOCIAL_PROOF_WEIGHT * socialProof
                    + CREATOR_INTERACTION_WEIGHT * creatorInteraction
                    - interactionPenalty
                    - watchedPenalty
                    - categoryPenalty
                    + categoryBoost;

            scored.add(new ScoredVideo(vid, v.getAuthorUserId(),
                    vc != null ? vc.getTextCategory() : null, score,
                    contentMatch, qualityScore, creatorAff, behavioral,
                    socialScore, personalHist, popularity, freshness,
                    userTypeBonus, exploration, watchPenalty, socialProof,
                    creatorInteraction));
        }

        scored.sort(Comparator.comparingDouble(ScoredVideo::score).reversed());
        return scored;
    }

    /**
     * 互动惩罚 (时间衰减): 点赞/收藏过同一视频的惩罚随天数递减。
     * 刚赞过的视频几乎不再推, 但 30 天前赞的只给轻微惩罚, 90 天前基本不惩罚。
     */
    private double calcInteractionPenalty(Long videoId,
                                           Map<Long, LocalDateTime> likedMap,
                                           Map<Long, LocalDateTime> collectedMap) {
        LocalDateTime likedAt = likedMap.get(videoId);
        LocalDateTime collectedAt = collectedMap.get(videoId);

        if (likedAt != null) {
            long daysAgo = ChronoUnit.DAYS.between(likedAt, LocalDateTime.now());
            double decay = Math.max(0.1, Math.pow(0.5, (double) daysAgo / 30.0)); // 30天半衰, 最低10%
            return 0.95 * decay;
        }
        if (collectedAt != null) {
            long daysAgo = ChronoUnit.DAYS.between(collectedAt, LocalDateTime.now());
            double decay = Math.max(0.1, Math.pow(0.5, (double) daysAgo / 30.0));
            return 0.80 * decay;
        }
        return 0.0;
    }

    /**
     * 品类反馈 (合并查询): 一次查询 watch_history + 一次查询 video_content，
     * 同时计算正反馈 (完整看完) 和负反馈 (快速划走)。
     */
    private CategoryFeedback calcCategoryFeedback(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<WatchHistory> recentWatches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .ge(WatchHistory::getCreateTime, since));
        if (recentWatches.isEmpty()) return new CategoryFeedback(Map.of(), Map.of());

        // 拆分为快划和完整观看两组
        List<Long> skippedIds = new ArrayList<>();
        List<Long> wellWatchedIds = new ArrayList<>();
        for (WatchHistory w : recentWatches) {
            Double swipe = w.getSwipeSeconds();
            if (swipe != null && swipe < 3.0) {
                skippedIds.add(w.getVideoId());
            }
            Double wd = w.getWatchDuration();
            Double vd = w.getVideoDuration();
            if (wd != null && vd != null && vd > 0 && (wd / vd) > 0.8) {
                wellWatchedIds.add(w.getVideoId());
            }
        }

        // 一次查询两个集合的视频品类
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(skippedIds);
        allIds.addAll(wellWatchedIds);
        if (allIds.isEmpty()) return new CategoryFeedback(Map.of(), Map.of());

        List<VideoContent> contents = contentMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .in(VideoContent::getVideoId, allIds)
                        .isNotNull(VideoContent::getTextCategory));

        Set<Long> skipSet = new HashSet<>(skippedIds);
        Set<Long> wellSet = new HashSet<>(wellWatchedIds);
        Map<String, Integer> skipCounts = new HashMap<>();
        Map<String, Integer> likeCounts = new HashMap<>();
        for (VideoContent vc : contents) {
            String cat = vc.getTextCategory();
            if (cat == null) continue;
            if (skipSet.contains(vc.getVideoId())) skipCounts.merge(cat, 1, Integer::sum);
            if (wellSet.contains(vc.getVideoId())) likeCounts.merge(cat, 1, Integer::sum);
        }

        Map<String, Double> penalties = new HashMap<>();
        for (var entry : skipCounts.entrySet()) {
            int count = entry.getValue();
            if (count >= 3) {
                penalties.put(entry.getKey(), Math.min(0.20, count * 0.02));
            }
        }

        Map<String, Double> boosts = new HashMap<>();
        for (var entry : likeCounts.entrySet()) {
            int count = entry.getValue();
            if (count >= 2) {
                boosts.put(entry.getKey(), Math.min(0.12, count * 0.02));
            }
        }

        return new CategoryFeedback(penalties, boosts);
    }

    /**
     * 全局搜索趋势: 从最近活跃用户的搜索记录中提取高频关键词。
     * 如果最近很多人在搜"世界杯"，相关视频(哪怕是旧的)应该被唤醒。
     */
    private List<String> getTrendingKeywords(int topN) {
        long now = System.currentTimeMillis();
        if (now - trendingKeywordsCacheTime < TRENDING_CACHE_MS) {
            return cachedTrendingKeywords;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(3);
            List<UserContentProfile> recentProfiles = profileMapper.selectList(
                    new LambdaQueryWrapper<UserContentProfile>()
                            .ge(UserContentProfile::getUpdateTime, since)
                            .isNotNull(UserContentProfile::getRecentSearchQueries)
                            .last("LIMIT 200"));
            Map<String, Integer> keywordFreq = new HashMap<>();
            for (UserContentProfile p : recentProfiles) {
                List<String> queries = parseRecentSearches(p.getRecentSearchQueries());
                for (String q : queries) {
                    if (q != null && q.length() >= 2) {
                        keywordFreq.merge(q.toLowerCase(), 1, Integer::sum);
                    }
                }
            }
            List<String> result = keywordFreq.entrySet().stream()
                    .filter(e -> e.getValue() >= 2)  // 至少2人搜过
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(topN)
                    .map(Map.Entry::getKey)
                    .toList();
            cachedTrendingKeywords = result;
            trendingKeywordsCacheTime = now;
            return result;
        } catch (Exception e) {
            cachedTrendingKeywords = List.of();
            trendingKeywordsCacheTime = now;
            return List.of();
        }
    }

    // --- 各信号计算方法 ---

    /** 内容匹配: 长期+短期向量双路余弦相似度 + 品类加成 */
    private double calcContentMatch(List<Double> longVec, List<Double> shortVec, VideoContent vc) {
        if (vc == null || vc.getContentVector() == null) return 0.3;
        List<Double> videoVec = parseVector(vc.getContentVector());
        if (videoVec == null) return 0.3;

        double longSim = longVec != null ? cosineSim(longVec, videoVec) : 0.0;
        double shortSim = shortVec != null ? cosineSim(shortVec, videoVec) : longSim;

        return clamp(longSim * 0.6 + shortSim * 0.4, 0, 1);
    }

    /**
     * 质量分: 互动率 + 内容质量 + 全站完播率 + 作者粉丝量。
     * 完播率高说明用户普遍看完该视频 → 质量好的强信号。
     */
    private double calcQualityScore(Video v, VideoContent vc, long followerCount, double avgCompletion) {
        double playCount = Math.max(1, v.getPlayCount() != null ? v.getPlayCount() : 1);
        long likes = v.getLikeCount() != null ? v.getLikeCount() : 0;
        long comments = v.getCommentCount() != null ? v.getCommentCount() : 0;
        long collects = v.getCollectCount() != null ? v.getCollectCount() : 0;
        long shares = v.getShareCount() != null ? v.getShareCount() : 0;

        // 互动率: 收藏和转发是更强的信号 (各计 2 倍权重)
        double interactions = likes + comments + collects * 2.0 + shares * 2.0;
        double engageRate = Math.min(1.0, interactions / playCount * 5);

        // AI 内容质量分
        double contentQuality = vc != null && vc.getQualityScore() != null ? vc.getQualityScore() : 0.5;

        // 作者粉丝量对数归一化
        double followerScore = 0.0;
        if (followerCount > 0) {
            followerScore = Math.min(1.0, Math.log(followerCount + 1) / Math.log(1_000_000));
        }

        // 全站完播率: 大家普遍看完 = 内容质量好
        double completionScore = clamp(avgCompletion, 0, 1);

        // 加权: 互动率(0.30) + 内容质量(0.25) + 完播率(0.25) + 粉丝量(0.20)
        return engageRate * 0.30 + contentQuality * 0.25 + completionScore * 0.25 + followerScore * 0.20;
    }

    /** 创作者亲和力: 用户对该创作者的历史喜好度 */
    private double calcCreatorAffinity(Long authorId, Map<String, Double> affinity) {
        if (authorId == null || affinity.isEmpty()) return 0.1;
        return clamp(affinity.getOrDefault(String.valueOf(authorId), 0.0), -0.5, 1.0);
    }

    /** 行为匹配: 搜索词匹配 (渐进) + 趋势关键词 + 品类偏好 + 时长偏好 */
    private double calcBehavioralMatch(Video v, VideoContent vc,
                                       List<String> recentSearches,
                                       Map<String, Double> catWeights,
                                       String userType,
                                       List<String> trendingKeywords) {
        double score = 0.1;

        // 搜索词渐进匹配: 全匹配 +0.3, 部分词匹配 +0.1/词
        if (v.getDesc() != null) {
            String descLower = v.getDesc().toLowerCase();
            List<String> descWords = Arrays.asList(descLower.split("\\s+"));

            if (!recentSearches.isEmpty()) {
                for (String query : recentSearches) {
                    if (query == null) continue;
                    String qLower = query.toLowerCase();
                    if (descLower.contains(qLower)) {
                        score += 0.3;  // 全匹配
                        break;
                    }
                    // 部分词匹配: 搜索词拆分后匹配
                    for (String qWord : qLower.split("\\s+")) {
                        if (qWord.length() >= 2 && descLower.contains(qWord)) {
                            score += 0.08;
                        }
                    }
                }
            }

            // 趋势关键词匹配: 全局热点唤醒旧视频
            if (!trendingKeywords.isEmpty()) {
                int trendHits = 0;
                for (String kw : trendingKeywords) {
                    if (descLower.contains(kw)) trendHits++;
                }
                score += Math.min(0.2, trendHits * 0.06);
            }
        }

        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(), 0.1);
            score += catWeight * 0.2;
        }

        if (v.getDuration() != null) {
            double dur = v.getDuration();
            if (dur >= 10 && dur <= 90) score += 0.1;
        }

        return clamp(score, 0, 1);
    }

    /** 社交分: 关注 1.0, 间接关系 0.3, 无关系 0.1 */
    private double calcSocialScore(Long userId, Video v,
                                    Map<Long, Double> authorCache, Set<Long> followed) {
        Long authorId = v.getAuthorUserId();
        if (authorId == null || authorId.equals(userId)) return 0.1;
        return authorCache.computeIfAbsent(authorId, aid -> {
            if (followed.contains(aid)) return 1.0;
            if (followed.isEmpty()) return 0.1;
            Long indirectCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowId, aid)
                    .in(Follow::getUserId, followed));
            if (indirectCount > 0) return 0.3;
            return 0.1;
        });
    }

    /** 个人历史: 基于用户观看行为模式 */
    private double calcPersonalHistory(Long userId, Video v, VideoContent vc,
                                       UserContentProfile profile) {
        double score = 0.1;

        double completionRate = profile.getAvgCompletionRate() != null
                ? profile.getAvgCompletionRate() : 0.5;
        double repeatRate = profile.getRepeatViewRate() != null
                ? profile.getRepeatViewRate() : 0.1;
        if (repeatRate > 0.3 && completionRate > 0.7) {
            score += 0.2;
        }

        Double prefMin = profile.getPreferredDurationMin();
        Double prefMax = profile.getPreferredDurationMax();
        if (prefMin != null && prefMax != null && v.getDuration() != null) {
            double dur = v.getDuration();
            if (dur >= prefMin && dur <= prefMax) score += 0.15;
            else if (dur >= prefMin * 0.5 && dur <= prefMax * 1.5) score += 0.05;
        }

        if (vc != null && vc.getMusicBpm() != null
                && profile.getPreferredBpmMin() != null && profile.getPreferredBpmMax() != null) {
            double bpm = vc.getMusicBpm();
            if (bpm >= profile.getPreferredBpmMin() && bpm <= profile.getPreferredBpmMax()) {
                score += 0.1;
            }
        }

        return clamp(score, 0, 1);
    }

    /** 热度趋势: 近期互动增长速度 */
    private double calcPopularityTrend(Video v) {
        if (v.getCreateTime() == null || v.getPlayCount() == null) return 0.3;
        long hoursSinceCreation = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        if (hoursSinceCreation <= 0) hoursSinceCreation = 1;

        double interactionsPerHour = (double)
                ((v.getLikeCount() != null ? v.getLikeCount() : 0)
                        + (v.getCommentCount() != null ? v.getCommentCount() : 0))
                / hoursSinceCreation;

        double trend = 1.0 / (1.0 + Math.exp(-0.5 * (interactionsPerHour - 3.0)));
        return clamp(trend, 0, 1);
    }

    /**
     * 新鲜度: 发布时间 + 近期互动活跃度。
     *
     * 不再是单纯的时间衰减！近期互动活跃的旧视频也会得到较高新鲜分。
     * - 新发布 + 高互动 → 1.0 (爆款新视频)
     * - 旧视频 + 近期翻红 (高pulse) → 0.6~0.8 (翻红视频不应被时间压死)
     * - 旧视频 + 彻底沉寂 → 指数衰减 (自然淘汰)
     * - 新发布 + 无人问津 → 0.3~0.5 (新但没人看)
     */
    private double calcFreshness(Video v, RecentEngagement re) {
        if (v.getCreateTime() == null) return 0.5;
        long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        double timeScore = Math.pow(0.5, (double) hours / FRESHNESS_HALF_LIFE_HOURS);
        double pulse = re != null ? re.pulse() : 0.0;

        // 近期有互动 → 用互动活跃度修正时间衰减
        if (re != null && re.isActive()) {
            // 活跃度越高，时间衰减越弱 (翻红视频的时效分不低于 0.5)
            return Math.max(0.5, timeScore * 0.3 + pulse * 0.7);
        }

        // 无近期互动 → 正常时间衰减
        return timeScore;
    }

    /** 用户类型加成 */
    private double calcUserTypeBonus(String userType, Video v, VideoContent vc,
                                     UserContentProfile profile) {
        switch (userType) {
            case "passive_consumer":
                double quality = vc != null && vc.getQualityScore() != null ? vc.getQualityScore() : 0.5;
                return quality * 0.8 + 0.2;
            case "social_butterfly":
                return 0.5;
            case "power_liker":
                double engageRate = v.getLikeCount() != null && v.getPlayCount() != null
                        ? (double) v.getLikeCount() / Math.max(1, v.getPlayCount()) : 0;
                return clamp(engageRate * 5, 0, 1);
            case "collector":
                if (vc != null && vc.getTextCategory() != null) {
                    String cat = vc.getTextCategory();
                    if (cat.contains("知识") || cat.contains("教程") || cat.contains("美食")) return 0.8;
                }
                return 0.4;
            case "active_searcher":
                return 0.5;
            case "creator_fan":
                String affStr = profile.getCreatorAffinity();
                if (affStr != null && v.getAuthorUserId() != null
                        && affStr.contains(String.valueOf(v.getAuthorUserId()))) {
                    return 0.9;
                }
                return 0.3;
            case "explorer":
                return 0.6;
            case "new_user":
                return 0.7;
            default:
                return 0.5;
        }
    }

    /** 探索激励: 给用户未充分接触的品类/新作者适当加权 */
    private double calcExplorationBonus(Video v, VideoContent vc,
                                        Map<String, Double> catWeights,
                                        UserContentProfile profile) {
        double bonus = 0.0;

        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(), 0.0);
            bonus += (1.0 - catWeight) * 0.4;
        }

        String affStr = profile.getCreatorAffinity();
        if (affStr == null || !affStr.contains(String.valueOf(v.getAuthorUserId()))) {
            bonus += 0.15;
        }

        if (vc != null && vc.getQualityScore() != null && vc.getQualityScore() > 0.6) {
            bonus += 0.1;
        }

        return clamp(bonus, 0, 1);
    }

    // ===================== 新增信号 =====================

    /**
     * 观看历史惩罚: 用户已经看过这个视频？降权！
     *
     * 惩罚分级:
     * - 完整看完但未互动(点赞/收藏) → 强烈不感兴趣，惩罚 0.8~1.0
     * - 看了一部分但未互动 → 可能不感兴趣，惩罚 0.4~0.6
     * - 快速划走 (swipeSeconds < 3s) → 明显不感兴趣，惩罚 0.6~0.8
     * - 短时观看但用户整体 bounceRate 高 → 可能是正常浏览，惩罚 0.2
     * - 从未看过 → 惩罚 0
     */
    private double calcWatchHistoryPenalty(WatchHistory wh, Video v,
                                           UserContentProfile profile) {
        if (wh == null) return 0.0; // 从未看过，无惩罚

        double watchDuration = wh.getWatchDuration() != null ? wh.getWatchDuration() : 0;
        double videoDuration = wh.getVideoDuration() != null && wh.getVideoDuration() > 0
                ? wh.getVideoDuration() : v.getDuration() != null ? v.getDuration() : 60;
        boolean finished = wh.getFinished() != null && wh.getFinished() == 1;
        double swipeSeconds = wh.getSwipeSeconds() != null ? wh.getSwipeSeconds() : watchDuration;

        // 完播惩罚：完整看完但没有标记喜欢 → 消重
        if (finished) {
            // 完播了说明内容还不错，但如果重复推送会厌烦
            // 根据用户的重复观看率调节：repeatViewRate 高 = 喜欢反复看 → 惩罚低
            double repeatRate = profile.getRepeatViewRate() != null
                    ? profile.getRepeatViewRate() : 0.1;
            return clamp(0.8 - repeatRate * 0.5, 0.3, 1.0);
        }

        // 快速划走：3 秒内就划走了 → 明显不感兴趣
        if (swipeSeconds < 3.0) {
            return 0.7;
        }

        // 看完一半以上的 → 有一定兴趣但不够，中等惩罚
        double completion = watchDuration / Math.max(1, videoDuration);
        if (completion > 0.5) {
            return 0.4;
        }

        // 快速划走但用户本身浏览率高（习惯性快划）→ 降低惩罚
        double bounceRate = profile.getBounceRate() != null ? profile.getBounceRate() : 0.3;
        if (completion < 0.3 && bounceRate > 0.5) {
            return 0.2; // 用户习惯性快划，不一定是内容差
        }

        // 看了一小部分 → 可能不感兴趣
        return 0.5;
    }

    /**
     * 社会证明: 综合博主影响力 + 视频社交信号 + 动态时效因子。
     *
     * 关键: 不再用写死的时间衰减！改用近期互动活跃度动态判断:
     * - 近期互动活跃 (recent pulse 高) → 不衰减，视频正在翻红或持续火爆
     * - 近期互动沉寂 (recent pulse 低) → 逐步衰减，让位给新鲜内容
     *
     * 场景举例:
     * - 10年前100万赞 + 近期每天仍有赞 → 翻红中，不衰减 → 高分
     * - 10年前100万赞 + 近期无人问津 → 沉寂 → 衰减 → 不再压制新视频
     * - 昨天5万赞 → 新视频自然高分
     */
    private double calcSocialProof(Video v, long followerCount, RecentEngagement re) {
        long likes = v.getLikeCount() != null ? v.getLikeCount() : 0;
        long collects = v.getCollectCount() != null ? v.getCollectCount() : 0;
        long shares = v.getShareCount() != null ? v.getShareCount() : 0;
        long plays = v.getPlayCount() != null ? v.getPlayCount() : 1;

        // 对数归一化，避免大 V 垄断
        double followerScore = Math.min(1.0, Math.log(followerCount + 1) / Math.log(1_000_000));
        double likeScore = Math.min(1.0, Math.log(likes + 1) / Math.log(100_000));
        double collectScore = Math.min(1.0, Math.log(collects + 1) / Math.log(10_000));
        double shareScore = Math.min(1.0, Math.log(shares + 1) / Math.log(10_000));

        // 互动率 (相对于播放量): 高互动率说明内容质量好
        double totalInteractions = likes + collects * 2.0 + shares * 3.0;
        double interactionRate = Math.min(1.0, totalInteractions / Math.max(1, plays) * 10);

        double rawProof = followerScore * 0.10
                + likeScore * 0.10
                + collectScore * 0.30
                + shareScore * 0.25
                + interactionRate * 0.25;

        // 动态时效因子: 有近期互动 → 不衰减; 无近期互动 → 时间衰减
        if (v.getCreateTime() != null) {
            double pulse = re != null ? re.pulse() : 0.0;
            if (pulse < 0.02) {
                // 近期基本无互动 → 按年龄衰减 (30天半衰期)
                long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
                double timeDecay = Math.pow(0.5, (double) hours / SOCIAL_PROOF_HALF_LIFE_HOURS);
                rawProof *= timeDecay;
            }
            // pulse >= 0.02: 视频仍有近期互动 → 不衰减，保留完整社交证明
        }

        return rawProof;
    }

    /**
     * 创作者互动信号: 该视频的作者是不是我关注/点赞/收藏过的？
     *
     * 分层加分:
     * - 关注 + 互动过 (双重关系) → 0.9~1.0，该博主的新视频应优先推送
     * - 仅关注过 (未互动) → 0.7，优先推但略低于互动过的
     * - 仅互动过 (未关注) → 0.8，点赞/收藏说明喜欢该博主内容，值得推
     * - 新视频 (7天内) 额外加成 × 1.2，互动作者新视频胜出
     * - 无关系 → 0.0
     */
    private double calcCreatorInteraction(Video v, Set<Long> followedSet,
                                           Set<Long> interactedAuthorIds) {
        Long authorId = v.getAuthorUserId();
        if (authorId == null) return 0.0;

        boolean followed = followedSet.contains(authorId);
        boolean interacted = interactedAuthorIds.contains(authorId);

        if (!followed && !interacted) return 0.0;

        double baseScore;
        if (followed && interacted) {
            baseScore = 0.9;  // 双重关系: 关注+互动 → 最强信号
        } else if (interacted) {
            baseScore = 0.8;  // 仅互动: 点赞/收藏说明内容偏好
        } else {
            baseScore = 0.7;  // 仅关注: 想看他/她的内容
        }

        // 时效性加成: 互动作者 7 天内新视频额外提权
        if (v.getCreateTime() != null && interacted) {
            long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
            if (hours <= 168) { // 7 天内
                double recencyBoost = 1.0 + Math.max(0, (168.0 - hours) / 168.0) * 0.3;
                baseScore *= recencyBoost;
            }
        }

        return clamp(baseScore, 0, 1);
    }

    // ===================== 多样性重排 =====================

    private List<Long> reRank(List<ScoredVideo> scored, Map<Long, VideoContent> contentMap,
                               List<Long> exploreIds, int pageSize,
                               Map<Long, Long> videoAgeHours,
                               Map<Long, String> videoTypeMap) {
        Set<Long> exploreSet = new HashSet<>(exploreIds);
        List<ScoredVideo> mainPool = new ArrayList<>();
        List<ScoredVideo> explorePool = new ArrayList<>();

        for (ScoredVideo sv : scored) {
            if (exploreSet.contains(sv.videoId)) {
                explorePool.add(sv);
            } else {
                mainPool.add(sv);
            }
        }

        List<Long> result = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        Map<String, Integer> catCount = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        int recentCount = 0;
        int mainIdx = 0, exploreIdx = 0;
        int exploreEvery = Math.max(3, pageSize / Math.max(1, (int) (pageSize * EXPLORE_INJECT_RATE)));

        while (result.size() < pageSize) {
            boolean injectExplore = result.size() > 0 && result.size() % exploreEvery == 0;
            // 时间多样性: 已有半数以上是48h内视频 → 跳过新的优先选老的
            boolean needOlder = recentCount > result.size() / 2 && result.size() > 2;

            ScoredVideo picked = null;
            if (injectExplore && exploreIdx < explorePool.size()) {
                picked = explorePool.get(exploreIdx++);
            } else if (mainIdx < mainPool.size()) {
                int skipped = 0;
                while (mainIdx < mainPool.size()) {
                    ScoredVideo candidate = mainPool.get(mainIdx);
                    boolean isRecent = videoAgeHours.getOrDefault(candidate.videoId, 0L) < 48;

                    if (violatesDiversity(candidate, authorCount, catCount, typeCount, videoTypeMap)) {
                        mainIdx++;
                        skipped++;
                        if (skipped > 10) break;
                    } else if (needOlder && isRecent && skipped < 8) {
                        mainIdx++;
                        skipped++;
                    } else {
                        picked = candidate;
                        mainIdx++;
                        break;
                    }
                }
                if (skipped > 10) {
                    if (mainIdx < mainPool.size()) {
                        picked = mainPool.get(mainIdx++);
                    } else if (exploreIdx < explorePool.size()) {
                        picked = explorePool.get(exploreIdx++);
                    } else {
                        break;
                    }
                } else if (mainIdx >= mainPool.size() && result.isEmpty()) {
                    break;
                }
            } else if (exploreIdx < explorePool.size()) {
                picked = explorePool.get(exploreIdx++);
            } else {
                break;
            }

            if (picked != null && !result.contains(picked.videoId)) {
                result.add(picked.videoId);
                authorCount.merge(picked.authorId, 1, Integer::sum);
                if (picked.category != null) {
                    catCount.merge(picked.category, 1, Integer::sum);
                }
                String vtype = videoTypeMap.getOrDefault(picked.videoId, "unknown");
                typeCount.merge(vtype, 1, Integer::sum);
                if (videoAgeHours.getOrDefault(picked.videoId, 0L) < 48) recentCount++;
                picked = null;
            }
        }

        return result;
    }

    private boolean violatesDiversity(ScoredVideo sv, Map<Long, Integer> authorCount,
                                       Map<String, Integer> catCount,
                                       Map<String, Integer> typeCount,
                                       Map<Long, String> videoTypeMap) {
        if (authorCount.getOrDefault(sv.authorId, 0) >= MAX_PER_AUTHOR) return true;
        if (sv.category != null && catCount.getOrDefault(sv.category, 0) >= MAX_PER_CATEGORY) return true;
        String vtype = videoTypeMap.getOrDefault(sv.videoId, "unknown");
        if (typeCount.getOrDefault(vtype, 0) >= MAX_PER_TYPE) return true;
        return false;
    }

    // ===================== 曝光管理 =====================

    private Set<Long> getRecentExposures(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return exposureMapper.selectList(new LambdaQueryWrapper<VideoExposure>()
                .eq(VideoExposure::getUserId, userId)
                .ge(VideoExposure::getExposureTime, since)
                .select(VideoExposure::getVideoId))
                .stream().map(VideoExposure::getVideoId)
                .collect(Collectors.toSet());
    }

    private void recordExposuresBatch(Long userId, List<Long> videoIds) {
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

    // ===================== 工具方法 =====================

    private Map<Long, VideoContent> loadContentMap(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        return contentMapper.selectBatchIds(videoIds).stream()
                .collect(Collectors.toMap(VideoContent::getVideoId, vc -> vc, (a, b) -> a));
    }

    private Set<Long> getFollowedSet(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId).select(Follow::getFollowId))
                .stream().map(Follow::getFollowId).collect(Collectors.toSet());
    }

    private List<Double> parseVector(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private double cosineSim(List<Double> a, VideoContent vc) {
        if (a == null || vc == null || vc.getContentVector() == null) return 0.0;
        List<Double> b = parseVector(vc.getContentVector());
        return cosineSim(a, b);
    }

    private double cosineSim(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double categoryBonus(UserContentProfile profile, VideoContent vc) {
        if (profile == null || vc == null || vc.getTextCategory() == null) return 0;
        Map<String, Double> weights = parseCategoryWeights(profile.getCategoryWeights());
        return weights.getOrDefault(vc.getTextCategory(), 0.0) * 0.3;
    }

    private Map<String, Double> parseCategoryWeights(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Double> parseCreatorAffinityMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) { return Map.of(); }
    }

    private List<String> parseRecentSearches(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return List.of(); }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ===================== 内部类 =====================

    private record CandidateScore(Long videoId, double score) {}

    private record CategoryFeedback(Map<String, Double> skipPenalties, Map<String, Double> likeBoosts) {}

    /** 打分后的候选视频 (13 维信号) */
    public record ScoredVideo(
            Long videoId,
            Long authorId,
            String category,
            double score,
            double contentMatch,
            double qualityScore,
            double creatorAffinity,
            double behavioralMatch,
            double socialScore,
            double personalHistory,
            double popularityTrend,
            double freshness,
            double userTypeBonus,
            double explorationBonus,
            double watchHistoryPenalty,
            double socialProof,
            double creatorInteraction
    ) {}
}
