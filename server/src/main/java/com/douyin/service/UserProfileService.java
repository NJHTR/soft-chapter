package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 全行为链路用户画像服务
 *
 * 数据源: 观看历史 / 曝光记录 / 点赞 / 收藏 / 评论互动 /
 *        关注链 / 搜索记录 / 主页浏览 / 私信
 *
 * 产出: UserContentProfile (多维度画像特征)
 */
@Slf4j
@Service
public class UserProfileService {

    private static final int SHORT_TERM_HOURS = 24;
    private static final int LONG_TERM_DAYS = 30;
    private static final double EMA_ALPHA_SHORT = 0.15;
    private static final double EMA_ALPHA_LONG = 0.05;
    private static final double CATEGORY_DECAY = 0.92;  // 品类权重日衰减

    private final UserContentProfileMapper profileMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoExposureMapper exposureMapper;
    private final LikeMapper likeMapper;
    private final VideoCollectMapper collectMapper;
    private final FollowMapper followMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final VisitorMapper visitorMapper;
    private final VideoContentMapper contentMapper;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserContentProfileMapper profileMapper,
                              WatchHistoryMapper watchHistoryMapper,
                              VideoExposureMapper exposureMapper,
                              LikeMapper likeMapper,
                              VideoCollectMapper collectMapper,
                              FollowMapper followMapper,
                              SearchHistoryMapper searchHistoryMapper,
                              VisitorMapper visitorMapper,
                              VideoContentMapper contentMapper) {
        this.profileMapper = profileMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.exposureMapper = exposureMapper;
        this.likeMapper = likeMapper;
        this.collectMapper = collectMapper;
        this.followMapper = followMapper;
        this.searchHistoryMapper = searchHistoryMapper;
        this.visitorMapper = visitorMapper;
        this.contentMapper = contentMapper;
        this.objectMapper = new ObjectMapper();
    }

    // ===================== 画像获取 =====================

    public UserContentProfile getOrCreate(Long userId) {
        UserContentProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserContentProfile();
            profile.setUserId(userId);
            profile.setUserType("balanced");
            profile.setUserSegment("new_user");
            profile.setTotalWatchCount(0);
            profile.setTotalViewCount(0);
            profile.setTotalLikeCount(0);
            profile.setTotalCollectCount(0);
            profile.setTotalShareCount(0);
            profile.setTotalCommentCount(0);
            profile.setTotalInteractCount(0);
            profile.setTotalSearchCount(0);
            profile.setTotalWatchTimeSec(0L);
            profile.setBounceRate(0.0);
            profile.setLikeRate(0.0);
            profile.setCollectRate(0.0);
            profile.setShareRate(0.0);
            profile.setCommentRate(0.0);
            profile.setRepeatViewRate(0.0);
            profile.setSocialEngagementRate(0.0);
            profile.setProfileVisitCount(0);
            profile.setSearchFrequency(0.0);
            profile.setAvgWatchDuration(0.0);
            profile.setAvgCompletionRate(0.0);
            profile.setAvgSessionDuration(0.0);
            profile.setActiveDaysLastWeek(0);
        }
        return profile;
    }

    // ===================== 实时增量更新 =====================

    /** 观看视频后更新画像 */
    public void onWatch(Long userId, Long videoId, Long authorId, double watchDurationSec,
                        double videoDurationSec, String trafficSource,
                        String sessionId, double swipeSeconds) {
        UserContentProfile p = getOrCreate(userId);
        VideoContent vc = contentMapper.selectById(videoId);

        boolean finished = videoDurationSec > 0 && watchDurationSec >= videoDurationSec * 0.9;
        boolean isBounce = watchDurationSec < 3.0;
        double completion = videoDurationSec > 0
                ? Math.min(1.0, watchDurationSec / videoDurationSec) : 0;

        long totalViews = (p.getTotalViewCount() != null ? p.getTotalViewCount() : 0) + 1;
        p.setTotalViewCount((int) totalViews);

        // 平均观看时长 (EMA)
        double oldAvg = p.getAvgWatchDuration() != null ? p.getAvgWatchDuration() : 0;
        p.setAvgWatchDuration(oldAvg * 0.9 + watchDurationSec * 0.1);

        // 完播率
        double oldComp = p.getAvgCompletionRate() != null ? p.getAvgCompletionRate() : 0;
        p.setAvgCompletionRate(oldComp * 0.95 + completion * 0.05);

        // 快速划走率
        double oldBounce = p.getBounceRate() != null ? p.getBounceRate() : 0;
        p.setBounceRate(oldBounce * 0.95 + (isBounce ? 1.0 : 0.0) * 0.05);

        // 观看次数
        p.setTotalWatchCount((p.getTotalWatchCount() != null ? p.getTotalWatchCount() : 0) + 1);

        // 总观看时长
        p.setTotalWatchTimeSec((p.getTotalWatchTimeSec() != null ? p.getTotalWatchTimeSec() : 0)
                + (long) watchDurationSec);

        // 内容向量: 短期 EMA 更新
        if (vc != null && vc.getContentVector() != null) {
            updateContentVectorEMA(p, vc.getContentVector(), true);
            updateCategoryWeights(p, vc.getTextCategory(), watchDurationSec);
            updateCreatorAffinity(p, authorId, watchDurationSec, isBounce);
        }

        // 流量来源分布
        if (trafficSource != null) {
            updateTrafficSources(p, trafficSource);
        }

        // 活跃时段
        updateActiveHours(p);

        // 活跃天数
        updateActiveDays(p);

        // 用户分层
        updateSegment(p);

        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 点赞 */
    public void onLike(Long userId, Long videoId, Long authorId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalLikeCount((p.getTotalLikeCount() != null ? p.getTotalLikeCount() : 0) + 1);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        updateRate("like", p);
        updateCreatorAffinity(p, authorId, 30, false); // like = 高权重正向信号
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 收藏 */
    public void onCollect(Long userId, Long videoId, Long authorId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalCollectCount((p.getTotalCollectCount() != null ? p.getTotalCollectCount() : 0) + 1);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        updateRate("collect", p);
        updateCreatorAffinity(p, authorId, 40, false); // 收藏 = 更高权重
        VideoContent vc = contentMapper.selectById(videoId);
        if (vc != null && vc.getContentVector() != null) {
            updateContentVectorEMA(p, vc.getContentVector(), true);
        }
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 分享 */
    public void onShare(Long userId, Long videoId, Long authorId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalShareCount((p.getTotalShareCount() != null ? p.getTotalShareCount() : 0) + 1);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        updateRate("share", p);
        updateCreatorAffinity(p, authorId, 35, false);
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 评论 */
    public void onComment(Long userId, Long videoId, Long authorId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalCommentCount((p.getTotalCommentCount() != null ? p.getTotalCommentCount() : 0) + 1);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        updateRate("comment", p);
        updateCreatorAffinity(p, authorId, 20, false);
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 关注 */
    public void onFollow(Long userId, Long followId) {
        UserContentProfile p = getOrCreate(userId);
        updateCreatorAffinity(p, followId, 50, false);
        p.setSocialEngagementRate(
                Math.min(1.0, (p.getSocialEngagementRate() != null ? p.getSocialEngagementRate() : 0) + 0.02));
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 搜索 */
    public void onSearch(Long userId, String keyword) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalSearchCount((p.getTotalSearchCount() != null ? p.getTotalSearchCount() : 0) + 1);

        // 搜索频率 (每10次观看的搜索次数)
        int views = Math.max(1, p.getTotalViewCount() != null ? p.getTotalViewCount() : 1);
        p.setSearchFrequency((double) (p.getTotalSearchCount() != null ? p.getTotalSearchCount() : 0) / views * 10);

        // 近期搜索词 (保留最近10条)
        List<String> recent = parseRecentSearches(p.getRecentSearchQueries());
        recent.add(0, keyword);
        if (recent.size() > 10) recent = recent.subList(0, 10);
        try {
            p.setRecentSearchQueries(objectMapper.writeValueAsString(recent));
        } catch (JsonProcessingException ignored) {}

        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 浏览他人主页 */
    public void onProfileVisit(Long userId, Long visitedUserId) {
        UserContentProfile p = getOrCreate(userId);
        p.setProfileVisitCount((p.getProfileVisitCount() != null ? p.getProfileVisitCount() : 0) + 1);
        p.setSocialEngagementRate(
                Math.min(1.0, (p.getSocialEngagementRate() != null ? p.getSocialEngagementRate() : 0) + 0.01));
        updateCreatorAffinity(p, visitedUserId, 5, false);
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 评论点赞 */
    public void onCommentLike(Long userId, Long commentId, Long videoId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    /** 评论点踩 */
    public void onCommentDislike(Long userId, Long commentId, Long videoId) {
        UserContentProfile p = getOrCreate(userId);
        p.setTotalInteractCount((p.getTotalInteractCount() != null ? p.getTotalInteractCount() : 0) + 1);
        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
    }

    // ===================== 全量画像计算 (周期性调用) =====================

    /** 为所有活跃用户重新计算画像 (建议每日凌晨执行) */
    public void rebuildAllProfiles() {
        List<UserContentProfile> profiles = profileMapper.selectList(null);
        int count = 0;
        for (UserContentProfile p : profiles) {
            try {
                rebuildProfile(p.getUserId());
                count++;
            } catch (Exception e) {
                log.warn("画像重建失败: userId={}", p.getUserId(), e);
            }
        }
        log.info("画像重建完成: {} 个用户", count);
    }

    /** 重建单个用户画像 */
    public void rebuildProfile(Long userId) {
        UserContentProfile p = getOrCreate(userId);

        // 从各数据源统计
        countWatches(p);
        countEngagements(p);
        countSocial(p);
        countSearch(p);
        countTrafficSources(p);
        countActiveHours(p);
        countActiveDays(p);

        // 用户类型分类
        classifyUserType(p);

        // 用户分层
        updateSegment(p);

        p.setUpdateTime(LocalDateTime.now());
        profileMapper.insertOrUpdate(p);
        log.debug("画像重建完成: userId={}, type={}, segment={}, views={}, likes={}",
                userId, p.getUserType(), p.getUserSegment(),
                p.getTotalViewCount(), p.getTotalLikeCount());
    }

    // ===================== 内部计算方法 =====================

    private void countWatches(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        List<WatchHistory> watches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .ge(WatchHistory::getCreateTime, since30d));

        int total = watches.size();
        p.setTotalViewCount(total);
        p.setTotalWatchCount(total);

        if (total == 0) return;

        // 统计
        double sumDuration = 0, sumCompletion = 0;
        int bounceCount = 0, repeatCount = 0;
        long totalTime = 0;

        for (WatchHistory w : watches) {
            double dur = w.getWatchDuration() != null ? w.getWatchDuration() : 0;
            double vdur = w.getVideoDuration() != null ? w.getVideoDuration() : 1;
            sumDuration += dur;
            sumCompletion += vdur > 0 ? Math.min(1.0, dur / vdur) : 0;
            if (dur < 3.0) bounceCount++;
            if (w.getRepeatCount() != null && w.getRepeatCount() > 1) repeatCount++;
            totalTime += (long) dur;
        }

        p.setAvgWatchDuration(sumDuration / total);
        p.setAvgCompletionRate(sumCompletion / total);
        p.setBounceRate((double) bounceCount / total);
        p.setRepeatViewRate((double) repeatCount / total);
        p.setTotalWatchTimeSec(totalTime);
    }

    private void countEngagements(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        long likeCount = likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId));
        long collectCount = collectMapper.selectCount(new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId).ge(VideoCollect::getCreateTime, since30d));

        int views = Math.max(1, p.getTotalViewCount() != null ? p.getTotalViewCount() : 1);

        p.setTotalLikeCount((int) likeCount);
        p.setTotalCollectCount((int) collectCount);
        p.setTotalInteractCount((int) (likeCount + collectCount));
        p.setLikeRate(Math.min(1.0, (double) likeCount / views));
        p.setCollectRate(Math.min(1.0, (double) collectCount / views));
        // share/comment rates 在增量更新中维护
    }

    private void countSocial(UserContentProfile p) {
        Long userId = p.getUserId();

        long profileVisits = visitorMapper.selectCount(new LambdaQueryWrapper<Visitor>()
                .eq(Visitor::getUserId, userId));
        p.setProfileVisitCount((int) profileVisits);

        // 社交互动率: 与关注作者的互动占比 (简化: 基于 profile 访问和关注数)
        long followingCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId));
        p.setSocialEngagementRate(followingCount > 0
                ? Math.min(1.0, (double) profileVisits / Math.max(1, followingCount) * 0.1)
                : 0.0);
    }

    private void countSearch(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        long searchCount = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId).ge(SearchHistory::getCreateTime, since30d));
        p.setTotalSearchCount((int) searchCount);

        int views = Math.max(1, p.getTotalViewCount() != null ? p.getTotalViewCount() : 1);
        p.setSearchFrequency((double) searchCount / views * 10);

        // 近期搜索词 (最近10条)
        List<SearchHistory> recent = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getCreateTime)
                        .last("LIMIT 10"));
        try {
            p.setRecentSearchQueries(objectMapper.writeValueAsString(
                    recent.stream().map(SearchHistory::getKeyword).toList()));
        } catch (JsonProcessingException ignored) {}
    }

    private void countTrafficSources(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        List<VideoExposure> exposures = exposureMapper.selectList(
                new LambdaQueryWrapper<VideoExposure>()
                        .eq(VideoExposure::getUserId, userId)
                        .ge(VideoExposure::getExposureTime, since30d));

        if (exposures.isEmpty()) return;

        Map<String, Long> sourceCounts = new HashMap<>();
        for (VideoExposure e : exposures) {
            String src = e.getTrafficSource() != null ? e.getTrafficSource() : "HOME_RECOMMEND";
            sourceCounts.merge(src, 1L, Long::sum);
        }

        long total = exposures.size();
        Map<String, Double> sourceRatios = new HashMap<>();
        sourceCounts.forEach((src, cnt) -> sourceRatios.put(src, (double) cnt / total));

        try {
            p.setTrafficSources(objectMapper.writeValueAsString(sourceRatios));
        } catch (JsonProcessingException ignored) {}
    }

    private void countActiveHours(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        List<WatchHistory> watches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .ge(WatchHistory::getCreateTime, since30d));

        if (watches.isEmpty()) return;

        int[] hours = new int[24];
        for (WatchHistory w : watches) {
            int h = w.getCreateTime().getHour();
            hours[h]++;
        }
        try {
            p.setActiveHours(objectMapper.writeValueAsString(hours));
        } catch (JsonProcessingException ignored) {}

        // 平均会话时长: 按 session_id 分组估算
        Map<String, List<WatchHistory>> sessions = watches.stream()
                .filter(w -> w.getSessionId() != null)
                .collect(Collectors.groupingBy(WatchHistory::getSessionId));
        if (!sessions.isEmpty()) {
            double avgSession = sessions.values().stream()
                    .mapToDouble(list -> list.size() * 15.0) // 粗略: 每个视频 ~15 秒
                    .average().orElse(0);
            p.setAvgSessionDuration(avgSession);
        }
    }

    private void countActiveDays(UserContentProfile p) {
        Long userId = p.getUserId();
        LocalDateTime since7d = LocalDateTime.now().minusDays(7);

        List<WatchHistory> watches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .ge(WatchHistory::getCreateTime, since7d));

        long activeDays = watches.stream()
                .map(w -> w.getCreateTime().toLocalDate())
                .distinct().count();
        p.setActiveDaysLastWeek((int) activeDays);
    }

    // ===================== 用户分类 =====================

    private void classifyUserType(UserContentProfile p) {
        double bounce = p.getBounceRate() != null ? p.getBounceRate() : 0;
        double likeRate = p.getLikeRate() != null ? p.getLikeRate() : 0;
        double collectRate = p.getCollectRate() != null ? p.getCollectRate() : 0;
        double searchFreq = p.getSearchFrequency() != null ? p.getSearchFrequency() : 0;
        double socialEng = p.getSocialEngagementRate() != null ? p.getSocialEngagementRate() : 0;
        int profileVisits = p.getProfileVisitCount() != null ? p.getProfileVisitCount() : 0;
        int totalViews = p.getTotalViewCount() != null ? p.getTotalViewCount() : 0;

        // 规则优先级
        if (totalViews < 20) { p.setUserType("balanced"); return; }
        if (bounce > 0.65 && likeRate < 0.05 && collectRate < 0.02) { p.setUserType("passive_consumer"); return; }
        if (socialEng > 0.3 && profileVisits > 10) { p.setUserType("social_butterfly"); return; }
        if (likeRate > 0.4 && collectRate < 0.05) { p.setUserType("power_liker"); return; }
        if (collectRate > 0.1) { p.setUserType("collector"); return; }
        if (searchFreq > 2.0) { p.setUserType("active_searcher"); return; }
        if (hasHighCreatorAffinity(p)) { p.setUserType("creator_fan"); return; }
        if (hasHighCategoryDiversity(p)) { p.setUserType("explorer"); return; }
        p.setUserType("balanced");
    }

    private boolean hasHighCreatorAffinity(UserContentProfile p) {
        if (p.getCreatorAffinity() == null) return false;
        try {
            Map<String, Double> aff = objectMapper.readValue(p.getCreatorAffinity(),
                    new TypeReference<Map<String, Double>>() {});
            return aff.values().stream().anyMatch(v -> v > 0.5);
        } catch (JsonProcessingException e) { return false; }
    }

    private boolean hasHighCategoryDiversity(UserContentProfile p) {
        if (p.getCategoryWeights() == null) return false;
        try {
            Map<String, Double> cats = objectMapper.readValue(p.getCategoryWeights(),
                    new TypeReference<Map<String, Double>>() {});
            int significantCats = (int) cats.values().stream().filter(v -> v > 0.1).count();
            return significantCats >= 5;
        } catch (JsonProcessingException e) { return false; }
    }

    private void updateSegment(UserContentProfile p) {
        int views = p.getTotalViewCount() != null ? p.getTotalViewCount() : 0;
        int activeDays = p.getActiveDaysLastWeek() != null ? p.getActiveDaysLastWeek() : 0;

        if (views < 50) p.setUserSegment("new_user");
        else if (views < 300 || activeDays < 3) p.setUserSegment("light");
        else if (views < 1500 || activeDays < 5) p.setUserSegment("medium");
        else p.setUserSegment("heavy");
    }

    // ===================== 向量/权重 增量更新 =====================

    private void updateContentVectorEMA(UserContentProfile p, String contentVectorJson, boolean isShortTerm) {
        List<Double> newVec = parseVector(contentVectorJson);
        if (newVec == null || newVec.isEmpty()) return;

        String existingJson = isShortTerm ? p.getShortTermVector() : p.getContentVector();
        List<Double> existing = parseVector(existingJson);

        double alpha = isShortTerm ? EMA_ALPHA_SHORT : EMA_ALPHA_LONG;
        String targetField = isShortTerm ? "shortTermVector" : "contentVector";

        if (existing == null || existing.size() != newVec.size()) {
            // 首次: 直接设置
            try {
                if (isShortTerm) {
                    p.setShortTermVector(contentVectorJson);
                } else {
                    p.setContentVector(contentVectorJson);
                }
            } catch (Exception ignored) {}
            return;
        }

        List<Double> updated = new ArrayList<>(existing.size());
        for (int i = 0; i < existing.size(); i++) {
            updated.add(existing.get(i) * (1 - alpha) + newVec.get(i) * alpha);
        }

        try {
            String json = objectMapper.writeValueAsString(updated);
            if (isShortTerm) p.setShortTermVector(json);
            else p.setContentVector(json);
        } catch (JsonProcessingException ignored) {}
    }

    private void updateCategoryWeights(UserContentProfile p, String category, double watchDurationSec) {
        if (category == null || category.isEmpty()) return;

        Map<String, Double> weights = parseCategoryWeights(p.getCategoryWeights());
        double increment = Math.min(0.3, watchDurationSec / 300.0); // 最多+0.3/次

        // 所有品类衰减
        weights.replaceAll((cat, w) -> Math.max(0.01, w * CATEGORY_DECAY));
        // 当前品类加成
        weights.merge(category, increment, (old, inc) -> Math.min(1.0, old + inc));

        try {
            p.setCategoryWeights(objectMapper.writeValueAsString(weights));
        } catch (JsonProcessingException ignored) {}
    }

    private void updateCreatorAffinity(UserContentProfile p, Long authorId, double increment, boolean isBounce) {
        if (authorId == null) return;
        double effectiveInc = isBounce ? -increment * 0.1 : increment;

        Map<String, Double> aff = parseCreatorAffinity(p.getCreatorAffinity());
        aff.merge(String.valueOf(authorId), Math.max(-1.0, effectiveInc),
                (old, inc) -> Math.max(-0.5, Math.min(1.0, old * 0.95 + inc * 0.01)));

        // 只保留 Top-50
        if (aff.size() > 50) {
            aff = aff.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(50)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (a, b) -> a, LinkedHashMap::new));
        }

        try {
            p.setCreatorAffinity(objectMapper.writeValueAsString(aff));
        } catch (JsonProcessingException ignored) {}
    }

    private void updateTrafficSources(UserContentProfile p, String source) {
        Map<String, Double> sources = parseTrafficSources(p.getTrafficSources());
        sources.merge(source, 0.02, (old, inc) -> Math.min(1.0, old + inc));
        // 衰减
        double total = sources.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            sources.replaceAll((k, v) -> v / total);
        }
        try {
            p.setTrafficSources(objectMapper.writeValueAsString(sources));
        } catch (JsonProcessingException ignored) {}
    }

    private void updateActiveHours(UserContentProfile p) {
        int hour = LocalDateTime.now().getHour();
        int[] hours = parseActiveHours(p.getActiveHours());
        hours[hour] = Math.min(255, hours[hour] + 1);
        try {
            p.setActiveHours(objectMapper.writeValueAsString(hours));
        } catch (JsonProcessingException ignored) {}
    }

    private void updateActiveDays(UserContentProfile p) {
        // 简化: 每次行为都标记当天活跃
        // 实际应在 daily batch 中重新计算
        if (p.getActiveDaysLastWeek() == null) p.setActiveDaysLastWeek(1);
    }

    // ===================== 行为率更新 =====================

    private void updateRate(String actionType, UserContentProfile p) {
        int views = Math.max(1, p.getTotalViewCount() != null ? p.getTotalViewCount() : 1);
        switch (actionType) {
            case "like":
                p.setLikeRate(Math.min(1.0,
                        (double) (p.getTotalLikeCount() != null ? p.getTotalLikeCount() : 0) / views));
                break;
            case "collect":
                p.setCollectRate(Math.min(1.0,
                        (double) (p.getTotalCollectCount() != null ? p.getTotalCollectCount() : 0) / views));
                break;
            case "comment":
                p.setCommentRate(Math.min(1.0,
                        (double) (p.getTotalCommentCount() != null ? p.getTotalCommentCount() : 0) / views));
                break;
            case "share":
                p.setShareRate(Math.min(1.0,
                        (double) (p.getTotalShareCount() != null ? p.getTotalShareCount() : 0) / views));
                break;
        }
    }

    // ===================== JSON 解析工具 =====================

    private List<Double> parseVector(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) { return null; }
    }

    private Map<String, Double> parseCategoryWeights(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) { return new HashMap<>(); }
    }

    private Map<String, Double> parseCreatorAffinity(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) { return new HashMap<>(); }
    }

    private Map<String, Double> parseTrafficSources(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) { return new HashMap<>(); }
    }

    private List<String> parseRecentSearches(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private int[] parseActiveHours(String json) {
        if (json == null || json.isEmpty()) return new int[24];
        try {
            return objectMapper.readValue(json, int[].class);
        } catch (Exception e) { return new int[24]; }
    }
}
