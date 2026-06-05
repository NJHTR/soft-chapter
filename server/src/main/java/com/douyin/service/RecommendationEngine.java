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
 * 召回通道: 内容召回 / 协同过滤 / 社交召回 / 热门召回 / 探索召回
 * 排序信号: 内容匹配 / 视频质量 / 创作者亲和力 / 行为匹配 /
 *          社交关系 / 个人历史 / 热度趋势 / 新鲜度 / 用户类型加成 / 探索激励
 */
@Slf4j
@Service
public class RecommendationEngine {

    private static final int RECALL_PER_CHANNEL = 50;
    private static final int CANDIDATE_POOL_SIZE = 200;

    // 排序权重 (总和 = 1.0)
    private static final double CONTENT_WEIGHT = 0.20;
    private static final double QUALITY_WEIGHT = 0.12;
    private static final double CREATOR_AFFINITY_WEIGHT = 0.10;
    private static final double BEHAVIORAL_WEIGHT = 0.08;
    private static final double SOCIAL_WEIGHT = 0.10;
    private static final double PERSONAL_HISTORY_WEIGHT = 0.08;
    private static final double POPULARITY_WEIGHT = 0.08;
    private static final double FRESHNESS_WEIGHT = 0.10;
    private static final double USER_TYPE_WEIGHT = 0.07;
    private static final double EXPLORATION_WEIGHT = 0.07;

    private static final long FRESHNESS_HALF_LIFE_HOURS = 48;
    private static final double EXPLORE_INJECT_RATE = 0.12;
    private static final int MAX_PER_AUTHOR = 2;
    private static final int MAX_PER_CATEGORY = 3;

    private final VideoMapper videoMapper;
    private final VideoContentMapper contentMapper;
    private final VideoExposureMapper exposureMapper;
    private final UserContentProfileMapper profileMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final UserProfileService profileService;
    private final ObjectMapper objectMapper;

    public RecommendationEngine(VideoMapper videoMapper, VideoContentMapper contentMapper,
                                VideoExposureMapper exposureMapper, UserContentProfileMapper profileMapper,
                                LikeMapper likeMapper, FollowMapper followMapper,
                                UserProfileService profileService) {
        this.videoMapper = videoMapper;
        this.contentMapper = contentMapper;
        this.exposureMapper = exposureMapper;
        this.profileMapper = profileMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.profileService = profileService;
        this.objectMapper = new ObjectMapper();
    }

    /** 主入口: 为用户推荐视频 */
    public List<Long> recommend(Long userId, int pageSize, Double minDuration) {
        Set<Long> excludeIds = getRecentExposures(userId);
        UserContentProfile profile = profileMapper.selectById(userId);

        // 1. 多路召回
        List<Long> cfCandidates = collaborativeRecall(userId, excludeIds, RECALL_PER_CHANNEL);
        List<Long> socialCandidates = socialRecall(userId, excludeIds, RECALL_PER_CHANNEL);
        List<Long> contentCandidates = contentRecall(profile, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> hotCandidates = hotRecall(excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> exploreCandidates = exploreRecall(profile, excludeIds, RECALL_PER_CHANNEL, minDuration);

        // 2. 合并去重
        Set<Long> candidateSet = new LinkedHashSet<>();
        candidateSet.addAll(contentCandidates);
        candidateSet.addAll(cfCandidates);
        candidateSet.addAll(socialCandidates);
        candidateSet.addAll(hotCandidates);
        candidateSet.addAll(exploreCandidates);
        candidateSet.removeAll(excludeIds);

        if (candidateSet.size() < pageSize) {
            // 补召回：简单拉最新视频
            List<Video> fallback = videoMapper.findRecallCandidates(
                    new ArrayList<>(excludeIds), minDuration, RECALL_PER_CHANNEL);
            fallback.forEach(v -> candidateSet.add(v.getId()));
        }

        List<Long> candidates = new ArrayList<>(candidateSet);
        if (candidates.isEmpty()) return List.of();

        // 3. 批量加载候选视频 + 内容特征
        List<Video> videos = videoMapper.selectBatchIds(
                candidates.subList(0, Math.min(candidates.size(), CANDIDATE_POOL_SIZE)));
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());

        // 4. 多信号打分
        List<ScoredVideo> scored = scoreVideos(userId, videos, contentMap, profile);

        // 5. 多样性重排
        List<Long> result = reRank(scored, contentMap, exploreCandidates, pageSize);

        // 6. 记录曝光
        recordExposuresBatch(userId, result);

        log.info("推荐完成: userId={} candidates={} result={} channels[content={} cf={} social={} hot={} explore={}]",
                userId, candidates.size(), result.size(),
                contentCandidates.size(), cfCandidates.size(), socialCandidates.size(),
                hotCandidates.size(), exploreCandidates.size());

        return result;
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

        // 加载内容向量，计算余弦相似度
        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Map<Long, VideoContent> contentMap = loadContentMap(videoIds);

        return videos.stream()
                .map(v -> {
                    double sim = cosineSim(userVec, contentMap.get(v.getId()));
                    // 品类加成
                    double catBonus = categoryBonus(profile, contentMap.get(v.getId()));
                    return new CandidateScore(v.getId(), sim + catBonus);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed())
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    /** 协同过滤召回: Item-based CF —— 共同被赞的视频 */
    private List<Long> collaborativeRecall(Long userId, Set<Long> excludeIds, int limit) {
        List<Long> likedIds = likeMapper.findRecentLikedVideoIds(userId, 50);
        if (likedIds.isEmpty()) return List.of();

        List<Map<String, Object>> rows = likeMapper.findCoLikedVideoIds(
                likedIds, new ArrayList<>(excludeIds), limit);
        return rows.stream()
                .map(r -> Long.valueOf(r.get("video_id").toString()))
                .toList();
    }

    /** 社交召回: 关注作者 + 最近互动作者的新视频 */
    private List<Long> socialRecall(Long userId, Set<Long> excludeIds, int limit) {
        Set<Long> authorIds = new LinkedHashSet<>();

        // 关注的作者
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId).last("LIMIT 100"));
        follows.forEach(f -> authorIds.add(f.getFollowId()));

        // 最近互动过的作者
        List<Long> recentAuthors = videoMapper.findRecentAuthorIds(userId, 20);
        authorIds.addAll(recentAuthors);

        if (authorIds.isEmpty()) return List.of();

        List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, authorIds)
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .orderByDesc(Video::getCreateTime)
                .last("LIMIT " + limit));
        return videos.stream().map(Video::getId).toList();
    }

    /** 热门召回: 高互动量 + 高质量分 */
    private List<Long> hotRecall(Set<Long> excludeIds, int limit, Double minDuration) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .last("LIMIT " + limit);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 探索召回: 用户未充分接触的品类 + 随机高质量 */
    private List<Long> exploreRecall(UserContentProfile profile, Set<Long> excludeIds,
                                      int limit, Double minDuration) {
        // 带内容特征的视频（质量有保证），随机抽样
        List<Video> videos = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, limit * 3);

        if (profile == null || profile.getCategoryWeights() == null) {
            // 无画像：完全随机
            Collections.shuffle(videos, ThreadLocalRandom.current());
            return videos.stream().map(Video::getId).limit(limit).toList();
        }

        // 优先推荐用户权重低的品类（探索）
        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());

        return videos.stream()
                .map(v -> {
                    VideoContent vc = contentMap.get(v.getId());
                    String cat = vc != null ? vc.getTextCategory() : null;
                    double exploreScore = cat != null ? (1.0 - catWeights.getOrDefault(cat, 0.0)) : 0.5;
                    // 加入随机扰动
                    return new CandidateScore(v.getId(),
                            exploreScore + ThreadLocalRandom.current().nextDouble() * 0.3);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed())
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    // ===================== 多信号打分 =====================

    private List<ScoredVideo> scoreVideos(Long userId, List<Video> videos,
                                           Map<Long, VideoContent> contentMap,
                                           UserContentProfile profile) {
        if (profile == null) profile = profileService.getOrCreate(userId);

        List<Double> userVec = parseVector(profile.getContentVector());
        List<Double> userVecShort = parseVector(profile.getShortTermVector());
        Map<String, Double> creatorAffinity = parseCreatorAffinityMap(profile.getCreatorAffinity());
        List<String> recentSearches = parseRecentSearches(profile.getRecentSearchQueries());
        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        String userType = profile.getUserType() != null ? profile.getUserType() : "balanced";
        Map<Long, Double> authorAffinityCache = new HashMap<>();
        Set<Long> followedSet = getFollowedSet(userId);

        List<ScoredVideo> scored = new ArrayList<>(videos.size());
        for (Video v : videos) {
            VideoContent vc = contentMap.get(v.getId());

            double contentMatch = calcContentMatch(userVec, userVecShort, vc);
            double qualityScore = calcQualityScore(v, vc);
            double creatorAff = calcCreatorAffinity(v.getAuthorUserId(), creatorAffinity);
            double behavioral = calcBehavioralMatch(v, vc, recentSearches, catWeights, userType);
            double socialScore = calcSocialScore(userId, v, authorAffinityCache, followedSet);
            double personalHist = calcPersonalHistory(userId, v, vc, profile);
            double popularity = calcPopularityTrend(v);
            double freshness = calcFreshness(v);
            double userTypeBonus = calcUserTypeBonus(userType, v, vc, profile);
            double exploration = calcExplorationBonus(v, vc, catWeights, profile);

            double score = CONTENT_WEIGHT * contentMatch
                    + QUALITY_WEIGHT * qualityScore
                    + CREATOR_AFFINITY_WEIGHT * creatorAff
                    + BEHAVIORAL_WEIGHT * behavioral
                    + SOCIAL_WEIGHT * socialScore
                    + PERSONAL_HISTORY_WEIGHT * personalHist
                    + POPULARITY_WEIGHT * popularity
                    + FRESHNESS_WEIGHT * freshness
                    + USER_TYPE_WEIGHT * userTypeBonus
                    + EXPLORATION_WEIGHT * exploration;

            scored.add(new ScoredVideo(v.getId(), v.getAuthorUserId(),
                    vc != null ? vc.getTextCategory() : null, score,
                    contentMatch, qualityScore, creatorAff, behavioral,
                    socialScore, personalHist, popularity, freshness,
                    userTypeBonus, exploration));
        }

        scored.sort(Comparator.comparingDouble(ScoredVideo::score).reversed());
        return scored;
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

    /** 质量分: 互动率 + 内容质量分 */
    private double calcQualityScore(Video v, VideoContent vc) {
        double playCount = Math.max(1, v.getPlayCount() != null ? v.getPlayCount() : 1);
        double interactions = (v.getLikeCount() != null ? v.getLikeCount() : 0)
                + (v.getCommentCount() != null ? v.getCommentCount() : 0)
                + (v.getCollectCount() != null ? v.getCollectCount() : 0)
                + (v.getShareCount() != null ? v.getShareCount() : 0);
        double engageRate = Math.min(1.0, interactions / playCount * 5);

        double contentQuality = vc != null && vc.getQualityScore() != null ? vc.getQualityScore() : 0.5;
        return engageRate * 0.5 + contentQuality * 0.5;
    }

    /** 创作者亲和力: 用户对该创作者的历史喜好度 */
    private double calcCreatorAffinity(Long authorId, Map<String, Double> affinity) {
        if (authorId == null || affinity.isEmpty()) return 0.1;
        return clamp(affinity.getOrDefault(String.valueOf(authorId), 0.0), -0.5, 1.0);
    }

    /** 行为匹配: 近期搜索词 + 品类偏好 + 用户类型 */
    private double calcBehavioralMatch(Video v, VideoContent vc,
                                       List<String> recentSearches,
                                       Map<String, Double> catWeights,
                                       String userType) {
        double score = 0.1;

        // 1. 搜索词匹配视频描述/关键词
        if (!recentSearches.isEmpty() && v.getDesc() != null) {
            String descLower = v.getDesc().toLowerCase();
            for (String query : recentSearches) {
                if (query != null && descLower.contains(query.toLowerCase())) {
                    score += 0.3;
                    break;
                }
            }
        }

        // 2. 品类偏好
        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(), 0.1);
            score += catWeight * 0.2;
        }

        // 3. 时长偏好匹配
        if (v.getDuration() != null) {
            // 短视频平台，大部分内容在 15-60 秒
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

        // 1. 重复观看概率: 高完播率用户倾向于重复观看相似内容
        double completionRate = profile.getAvgCompletionRate() != null
                ? profile.getAvgCompletionRate() : 0.5;
        double repeatRate = profile.getRepeatViewRate() != null
                ? profile.getRepeatViewRate() : 0.1;
        if (repeatRate > 0.3 && completionRate > 0.7) {
            score += 0.2;
        }

        // 2. 时长偏好匹配: 推荐用户习惯的时长范围
        Double prefMin = profile.getPreferredDurationMin();
        Double prefMax = profile.getPreferredDurationMax();
        if (prefMin != null && prefMax != null && v.getDuration() != null) {
            double dur = v.getDuration();
            if (dur >= prefMin && dur <= prefMax) score += 0.15;
            else if (dur >= prefMin * 0.5 && dur <= prefMax * 1.5) score += 0.05;
        }

        // 3. BPM 偏好 (如果用户有音乐偏好)
        if (vc != null && vc.getMusicBpm() != null
                && profile.getPreferredBpmMin() != null && profile.getPreferredBpmMax() != null) {
            double bpm = vc.getMusicBpm();
            if (bpm >= profile.getPreferredBpmMin() && bpm <= profile.getPreferredBpmMax()) {
                score += 0.1;
            }
        }

        // 4. 基于用户活跃度冷启动保护
        if (profile.getUserSegment() != null && "new_user".equals(profile.getUserSegment())) {
            score += 0.2; // 新用户给更多探索空间
        }

        return clamp(score, 0, 1);
    }

    /** 热度趋势: 近期互动增长速度 */
    private double calcPopularityTrend(Video v) {
        // 简化: 用互动密度 (点赞数/存在小时数) 衡量增长趋势
        if (v.getCreateTime() == null || v.getPlayCount() == null) return 0.3;
        long hoursSinceCreation = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        if (hoursSinceCreation <= 0) hoursSinceCreation = 1;

        double interactionsPerHour = (double)
                ((v.getLikeCount() != null ? v.getLikeCount() : 0)
                        + (v.getCommentCount() != null ? v.getCommentCount() : 0))
                / hoursSinceCreation;

        // Sigmoid 归一化 (1 互动/小时 = 0.5, 10 互动/小时 = 0.9)
        double trend = 1.0 / (1.0 + Math.exp(-0.5 * (interactionsPerHour - 3.0)));
        return clamp(trend, 0, 1);
    }

    /** 用户类型加成: 不同用户类型需要不同的推荐策略 */
    private double calcUserTypeBonus(String userType, Video v, VideoContent vc,
                                     UserContentProfile profile) {
        switch (userType) {
            case "passive_consumer":
                // 低互动用户: 高质量视频 + 热门内容
                double quality = vc != null && vc.getQualityScore() != null ? vc.getQualityScore() : 0.5;
                return quality * 0.8 + 0.2;

            case "social_butterfly":
                // 社交型: 关注作者的视频
                return 0.5; // socialScore 已经 cover 了

            case "power_liker":
                // 高度活跃: 奖励高质量互动内容
                double engageRate = v.getLikeCount() != null && v.getPlayCount() != null
                        ? (double) v.getLikeCount() / Math.max(1, v.getPlayCount()) : 0;
                return clamp(engageRate * 5, 0, 1);

            case "collector":
                // 收藏者: 教程/知识类内容
                if (vc != null && vc.getTextCategory() != null) {
                    String cat = vc.getTextCategory();
                    if (cat.contains("知识") || cat.contains("教程") || cat.contains("美食")) return 0.8;
                }
                return 0.4;

            case "active_searcher":
                // 搜索型: 内容与近期搜索的高匹配度
                return 0.5; // behavioralMatch 已经 cover

            case "creator_fan":
                // 粉丝型: 创作者亲和力高的内容
                String affStr = profile.getCreatorAffinity();
                if (affStr != null && v.getAuthorUserId() != null
                        && affStr.contains(String.valueOf(v.getAuthorUserId()))) {
                    return 0.9;
                }
                return 0.3;

            case "explorer":
                // 探索型: 新品类/新作者
                return 0.6;

            default: // balanced
                return 0.5;
        }
    }

    /** 探索激励: 给用户未充分接触的品类/新作者适当加权 */
    private double calcExplorationBonus(Video v, VideoContent vc,
                                        Map<String, Double> catWeights,
                                        UserContentProfile profile) {
        double bonus = 0.0;

        // 1. 新品类激励: 低权重品类获得更高探索分
        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(), 0.0);
            bonus += (1.0 - catWeight) * 0.4;
        }

        // 2. 新作者激励: 未接触过的作者
        String affStr = profile.getCreatorAffinity();
        if (affStr == null || !affStr.contains(String.valueOf(v.getAuthorUserId()))) {
            bonus += 0.15;
        }

        // 3. 高质量内容更容易被探索推荐
        if (vc != null && vc.getQualityScore() != null && vc.getQualityScore() > 0.6) {
            bonus += 0.1;
        }

        return clamp(bonus, 0, 1);
    }

    /** 新鲜度: 指数衰减, 半衰期48小时 */
    private double calcFreshness(Video v) {
        if (v.getCreateTime() == null) return 0.5;
        long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        return Math.pow(0.5, (double) hours / FRESHNESS_HALF_LIFE_HOURS);
    }

    // ===================== 多样性重排 =====================

    private List<Long> reRank(List<ScoredVideo> scored, Map<Long, VideoContent> contentMap,
                               List<Long> exploreIds, int pageSize) {
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
        int mainIdx = 0, exploreIdx = 0;
        int exploreEvery = Math.max(3, pageSize / Math.max(1, (int) (pageSize * EXPLORE_INJECT_RATE)));

        while (result.size() < pageSize) {
            boolean injectExplore = result.size() > 0 && result.size() % exploreEvery == 0;

            ScoredVideo picked = null;
            if (injectExplore && exploreIdx < explorePool.size()) {
                picked = explorePool.get(exploreIdx++);
            } else if (mainIdx < mainPool.size()) {
                // 跳过违反多样性约束的
                int skipped = 0;
                while (mainIdx < mainPool.size()) {
                    ScoredVideo candidate = mainPool.get(mainIdx);
                    if (violatesDiversity(candidate, authorCount, catCount)) {
                        mainIdx++;
                        skipped++;
                        if (skipped > 10) break; // 避免死循环
                    } else {
                        picked = candidate;
                        mainIdx++;
                        break;
                    }
                }
                if (skipped > 10) {
                    // 放宽约束, 取下一个可用的
                    if (mainIdx < mainPool.size()) {
                        picked = mainPool.get(mainIdx++);
                    } else if (exploreIdx < explorePool.size()) {
                        picked = explorePool.get(exploreIdx++);
                    } else {
                        break;
                    }
                } else if (mainIdx >= mainPool.size() && (skipped == 0 || result.isEmpty())) {
                    break;
                } else if (mainIdx >= mainPool.size() && result.size() > 0) {
                    continue;
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
                picked = null;
            }
        }

        return result;
    }

    private boolean violatesDiversity(ScoredVideo sv, Map<Long, Integer> authorCount,
                                       Map<String, Integer> catCount) {
        if (authorCount.getOrDefault(sv.authorId, 0) >= MAX_PER_AUTHOR) return true;
        if (sv.category != null && catCount.getOrDefault(sv.category, 0) >= MAX_PER_CATEGORY) return true;
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

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ===================== 内部类 =====================

    private record CandidateScore(Long videoId, double score) {}

    /** 打分后的候选视频 (10维信号) */
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
            double explorationBonus
    ) {}

    // ===================== JSON 解析扩展 =====================

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
}
