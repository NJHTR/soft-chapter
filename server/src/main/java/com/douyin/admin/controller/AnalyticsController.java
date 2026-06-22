package com.douyin.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.common.Result;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.douyin.service.ContentFeatureService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController("AdminAnalyticsController")
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final ActiveSessionMapper activeSessionMapper;
    private final LikeMapper likeMapper;
    private final VideoCollectMapper videoCollectMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final UserContentProfileMapper userContentProfileMapper;
    private final VideoContentMapper videoContentMapper;
    private final VideoTagMapper videoTagMapper;
    private final CommentMapper commentMapper;
    private final VideoExposureMapper videoExposureMapper;
    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final FollowMapper followMapper;
    private final JwtUtil jwtUtil;
    private final ContentFeatureService contentFeatureService;

    public AnalyticsController(UserMapper userMapper, VideoMapper videoMapper,
                               SearchHistoryMapper searchHistoryMapper,
                               LoginHistoryMapper loginHistoryMapper,
                               ActiveSessionMapper activeSessionMapper,
                               LikeMapper likeMapper,
                               VideoCollectMapper videoCollectMapper,
                               WatchHistoryMapper watchHistoryMapper,
                               UserContentProfileMapper userContentProfileMapper,
                               VideoContentMapper videoContentMapper,
                               VideoTagMapper videoTagMapper,
                               CommentMapper commentMapper,
                               VideoExposureMapper videoExposureMapper,
                               OrderMapper orderMapper,
                               GoodsMapper goodsMapper,
                               WalletTransactionMapper walletTransactionMapper,
                               LiveRoomMapper liveRoomMapper,
                               FollowMapper followMapper,
                               JwtUtil jwtUtil,
                               ContentFeatureService contentFeatureService) {
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
        this.searchHistoryMapper = searchHistoryMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.activeSessionMapper = activeSessionMapper;
        this.likeMapper = likeMapper;
        this.videoCollectMapper = videoCollectMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.userContentProfileMapper = userContentProfileMapper;
        this.videoContentMapper = videoContentMapper;
        this.videoTagMapper = videoTagMapper;
        this.commentMapper = commentMapper;
        this.videoExposureMapper = videoExposureMapper;
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.liveRoomMapper = liveRoomMapper;
        this.followMapper = followMapper;
        this.jwtUtil = jwtUtil;
        this.contentFeatureService = contentFeatureService;
    }

    private User checkAdmin(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.getUserIdFromToken(auth.substring(7));
                if (userId != null) {
                    User user = userMapper.selectById(userId);
                    if (user != null && "ADMIN".equals(user.getRole())) return user;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ========== Dashboard Overview ==========

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long onlineUsers = activeSessionMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.ActiveSession>()
                        .eq(com.douyin.entity.ActiveSession::getIsActive, 1));

        long todayNewVideos = videoMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.Video>()
                        .ge(com.douyin.entity.Video::getCreateTime, todayStart));

        long todayActiveUsers = watchHistoryMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.WatchHistory>()
                        .ge(com.douyin.entity.WatchHistory::getCreateTime, todayStart));

        long todayViews = watchHistoryMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.WatchHistory>()
                        .ge(com.douyin.entity.WatchHistory::getCreateTime, todayStart));

        long totalVideos = videoMapper.selectCount(null);
        long totalUsers = userMapper.selectCount(null);

        // Today's engagement
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        long todayLikes = likeMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.Like>()
                        .ge(com.douyin.entity.Like::getCreateTime, todayStart));
        long yesterdayLikes = likeMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.Like>()
                        .ge(com.douyin.entity.Like::getCreateTime, yesterdayStart)
                        .lt(com.douyin.entity.Like::getCreateTime, todayStart));

        long todaySearches = searchHistoryMapper.selectCount(
                new LambdaQueryWrapper<com.douyin.entity.SearchHistory>()
                        .ge(com.douyin.entity.SearchHistory::getCreateTime, todayStart));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onlineUsers", onlineUsers);
        data.put("todayNewVideos", todayNewVideos);
        data.put("todayActiveUsers", todayActiveUsers);
        data.put("todayViews", todayViews);
        data.put("todayLikes", todayLikes);
        data.put("yesterdayLikes", yesterdayLikes);
        data.put("todaySearches", todaySearches);
        data.put("totalVideos", totalVideos);
        data.put("totalUsers", totalUsers);
        return Result.ok(data);
    }

    // ========== Video Post Trend (hourly/daily/monthly) ==========

    @GetMapping("/video-post-trend")
    public Result<List<Map<String, Object>>> videoPostTrend(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<com.douyin.entity.Video> videos = videoMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.Video>()
                        .ge(com.douyin.entity.Video::getCreateTime, since)
                        .orderByAsc(com.douyin.entity.Video::getCreateTime));

        DateTimeFormatter fmt;
        if ("hourly".equals(period)) fmt = DateTimeFormatter.ofPattern("MM-dd HH:00");
        else if ("monthly".equals(period)) fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        else fmt = DateTimeFormatter.ofPattern("MM-dd");

        Map<String, Long> grouped = videos.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getCreateTime().format(fmt),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.ok(result);
    }

    // ========== Video Engagement Trend ==========

    @GetMapping("/engagement-trend")
    public Result<List<Map<String, Object>>> engagementTrend(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        DateTimeFormatter fmt = "hourly".equals(period)
                ? DateTimeFormatter.ofPattern("MM-dd HH:00")
                : DateTimeFormatter.ofPattern("MM-dd");

        // Likes per period
        List<com.douyin.entity.Like> likes = likeMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.Like>()
                        .ge(com.douyin.entity.Like::getCreateTime, since));

        Map<String, Long> likeGrouped = likes.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCreateTime().format(fmt),
                        LinkedHashMap::new,
                        Collectors.counting()));

        // Collects per period
        List<com.douyin.entity.VideoCollect> collects = videoCollectMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.VideoCollect>()
                        .ge(com.douyin.entity.VideoCollect::getCreateTime, since));

        Map<String, Long> collectGrouped = collects.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCreateTime().format(fmt),
                        LinkedHashMap::new,
                        Collectors.counting()));

        // Merge
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(likeGrouped.keySet());
        allKeys.addAll(collectGrouped.keySet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : allKeys) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", key);
            item.put("likes", likeGrouped.getOrDefault(key, 0L));
            item.put("collects", collectGrouped.getOrDefault(key, 0L));
            result.add(item);
        }
        return Result.ok(result);
    }

    // ========== Top Videos ==========

    @GetMapping("/top-videos")
    public Result<List<Map<String, Object>>> topVideos(
            @RequestParam(defaultValue = "views") String metric,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<com.douyin.entity.Video> videos = videoMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.Video>()
                        .eq(com.douyin.entity.Video::getStatus, "APPROVED")
                        .eq(com.douyin.entity.Video::getIsDelete, 0)
                        .orderByDesc("views".equals(metric) ? com.douyin.entity.Video::getPlayCount
                                : "likes".equals(metric) ? com.douyin.entity.Video::getLikeCount
                                : "shares".equals(metric) ? com.douyin.entity.Video::getShareCount
                                : "collects".equals(metric) ? com.douyin.entity.Video::getCollectCount
                                : com.douyin.entity.Video::getCommentCount)
                        .last("LIMIT " + limit));

        List<Map<String, Object>> result = new ArrayList<>();
        for (com.douyin.entity.Video v : videos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", v.getId());
            item.put("desc", v.getDesc());
            item.put("coverUrl", getEffectiveCover(v));
            item.put("playCount", v.getPlayCount());
            item.put("likeCount", v.getLikeCount());
            item.put("shareCount", v.getShareCount());
            item.put("collectCount", v.getCollectCount());
            item.put("commentCount", v.getCommentCount());
            item.put("createTime", v.getCreateTime());
            item.put("type", v.getType());
            result.add(item);
        }
        return Result.ok(result);
    }

    // ========== Search Stats ==========

    @GetMapping("/search-stats")
    public Result<Map<String, Object>> searchStats(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();

        // Top keywords
        List<com.douyin.entity.SearchHistory> histories = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.SearchHistory>()
                        .ge(com.douyin.entity.SearchHistory::getCreateTime, since));

        Map<String, Long> keywordCounts = histories.stream()
                .collect(Collectors.groupingBy(
                        com.douyin.entity.SearchHistory::getKeyword,
                        Collectors.counting()));

        // Sort by frequency desc, top 20
        List<Map<String, Object>> topKeywords = keywordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("keyword", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        // Daily search volume trend
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Long> dailyVolume = histories.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getCreateTime().format(fmt),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<Map<String, Object>> trend = new ArrayList<>();
        dailyVolume.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", k);
            item.put("count", v);
            trend.add(item);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topKeywords", topKeywords);
        result.put("trend", trend);
        result.put("totalSearches", histories.size());
        return Result.ok(result);
    }

    // ========== Geo Distribution ==========

    @GetMapping("/geo-data")
    public Result<List<Map<String, Object>>> geoData(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<com.douyin.entity.LoginHistory> logins = loginHistoryMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.LoginHistory>()
                        .isNotNull(com.douyin.entity.LoginHistory::getRegion)
                        .ne(com.douyin.entity.LoginHistory::getRegion, ""));

        Map<String, Long> regionCounts = logins.stream()
                .collect(Collectors.groupingBy(
                        l -> (l.getRegion() != null && !l.getRegion().isEmpty())
                                ? l.getRegion() : "未知",
                        Collectors.counting()));

        // Also count from user profiles
        List<com.douyin.entity.User> users = userMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.User>()
                        .isNotNull(com.douyin.entity.User::getProvince)
                        .ne(com.douyin.entity.User::getProvince, ""));

        Map<String, Long> userProvince = users.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getProvince(),
                        Collectors.counting()));

        // Merge
        Map<String, Long> merged = new LinkedHashMap<>(regionCounts);
        userProvince.forEach((k, v) -> merged.merge(k, v, Long::sum));

        List<Map<String, Object>> result = merged.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(34)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    // ========== Online Users Detail ==========

    @GetMapping("/online-users")
    public Result<Map<String, Object>> onlineUsers(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<com.douyin.entity.ActiveSession> sessions = activeSessionMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.ActiveSession>()
                        .eq(com.douyin.entity.ActiveSession::getIsActive, 1));

        long count = sessions.size();

        // Device breakdown
        Map<String, Long> deviceBreakdown = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getDeviceOs() != null ? s.getDeviceOs() : "Unknown",
                        Collectors.counting()));

        // City breakdown
        Map<String, Long> cityBreakdown = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCity() != null ? s.getCity() : "未知",
                        Collectors.counting()));

        List<Map<String, Object>> recentSessions = sessions.stream()
                .sorted(Comparator.comparing(
                        com.douyin.entity.ActiveSession::getLastActiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", s.getUserId());
                    item.put("deviceName", s.getDeviceName());
                    item.put("city", s.getCity());
                    item.put("loginTime", s.getLoginTime());
                    item.put("lastActiveTime", s.getLastActiveTime());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("deviceBreakdown", deviceBreakdown);
        result.put("cityBreakdown", cityBreakdown);
        result.put("recentSessions", recentSessions);
        return Result.ok(result);
    }

    // ========== User Activity Flow (login history over time) ==========

    @GetMapping("/user-activity-flow")
    public Result<List<Map<String, Object>>> userActivityFlow(
            @RequestParam(defaultValue = "24") int hours,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        List<com.douyin.entity.LoginHistory> logins = loginHistoryMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.LoginHistory>()
                        .ge(com.douyin.entity.LoginHistory::getCreateTime, since)
                        .orderByAsc(com.douyin.entity.LoginHistory::getCreateTime));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:00");
        Map<String, Long> hourly = logins.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCreateTime().format(fmt),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        hourly.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.ok(result);
    }

    // ========== User Profile / Portrait ==========

    @GetMapping("/user-profile/{uid}")
    public Result<Map<String, Object>> userProfile(@PathVariable Long uid, HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        User user = userMapper.selectById(uid);
        if (user == null) return Result.fail("User not found");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("uid", user.getUid());
        profile.put("nickname", user.getNickname());
        profile.put("email", user.getEmail());
        profile.put("avatar", user.getAvatar168Url());
        profile.put("signature", user.getSignature());
        profile.put("gender", user.getGender());
        profile.put("province", user.getProvince());
        profile.put("city", user.getCity());
        profile.put("role", user.getRole());
        profile.put("followerCount", user.getFollowerCount());
        profile.put("followingCount", user.getFollowingCount());
        profile.put("totalFavorited", user.getTotalFavorited());
        profile.put("videoCount", user.getVideoCount());
        profile.put("createTime", user.getCreateTime());

        // Content profile / portrait
        var cp = userContentProfileMapper.selectById(uid);
        if (cp != null) {
            Map<String, Object> portrait = new LinkedHashMap<>();
            portrait.put("userType", cp.getUserType());
            portrait.put("userSegment", cp.getUserSegment());
            portrait.put("categoryWeights", cp.getCategoryWeights());
            portrait.put("activeHours", cp.getActiveHours());
            portrait.put("avgWatchDuration", cp.getAvgWatchDuration());
            portrait.put("avgCompletionRate", cp.getAvgCompletionRate());
            portrait.put("bounceRate", cp.getBounceRate());
            portrait.put("likeRate", cp.getLikeRate());
            portrait.put("collectRate", cp.getCollectRate());
            portrait.put("shareRate", cp.getShareRate());
            portrait.put("commentRate", cp.getCommentRate());
            portrait.put("searchFrequency", cp.getSearchFrequency());
            portrait.put("activeDaysLastWeek", cp.getActiveDaysLastWeek());
            portrait.put("avgSessionDuration", cp.getAvgSessionDuration());
            portrait.put("preferredDurationMin", cp.getPreferredDurationMin());
            portrait.put("preferredDurationMax", cp.getPreferredDurationMax());
            portrait.put("trafficSources", cp.getTrafficSources());
            portrait.put("totalWatchCount", cp.getTotalWatchCount());
            portrait.put("totalLikeCount", cp.getTotalLikeCount());
            portrait.put("totalCollectCount", cp.getTotalCollectCount());
            portrait.put("totalShareCount", cp.getTotalShareCount());
            portrait.put("totalCommentCount", cp.getTotalCommentCount());
            portrait.put("totalSearchCount", cp.getTotalSearchCount());
            portrait.put("totalWatchTimeSec", cp.getTotalWatchTimeSec());
            portrait.put("recentSearchQueries", cp.getRecentSearchQueries());
            profile.put("portrait", portrait);
        }

        return Result.ok(profile);
    }

    // ========== Video Tag Management ==========

    /** 分页列举所有视频的标签摘要 */
    @GetMapping("/video-tags")
    public Result<Map<String, Object>> listVideoTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String extractStatus,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        // 查询 video + content (LEFT JOIN style, 分批)
        LambdaQueryWrapper<Video> videoWrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text", "long-video"))
                .eq(Video::getStatus, "APPROVED");
        if (keyword != null && !keyword.isBlank()) {
            videoWrapper.and(w -> w.like(Video::getDesc, keyword).or().eq(Video::getId,
                    tryParseLong(keyword)));
        }
        videoWrapper.orderByDesc(Video::getCreateTime);

        long total = videoMapper.selectCount(videoWrapper);
        int offset = (page - 1) * pageSize;
        List<Video> videos = videoMapper.selectList(videoWrapper.last("LIMIT " + offset + "," + pageSize));

        // 批量加载 content + tags
        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Map<Long, VideoContent> contentMap = Map.of();
        Map<Long, List<VideoTag>> tagMap = Map.of();
        if (!videoIds.isEmpty()) {
            contentMap = videoContentMapper.selectBatchIds(videoIds).stream()
                    .collect(Collectors.toMap(VideoContent::getVideoId, vc -> vc, (a, b) -> a));
            tagMap = videoTagMapper.selectList(new LambdaQueryWrapper<VideoTag>()
                    .in(VideoTag::getVideoId, videoIds)
                    .gt(VideoTag::getWeight, 0.05))
                    .stream().collect(Collectors.groupingBy(VideoTag::getVideoId));
        }

        // 组装
        List<Map<String, Object>> items = new ArrayList<>();
        for (Video v : videos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("videoId", v.getId());
            item.put("desc", v.getDesc());
            item.put("coverUrl", getEffectiveCover(v));
            item.put("type", v.getType());
            item.put("likeCount", v.getLikeCount());
            item.put("createTime", v.getCreateTime());

            VideoContent vc = contentMap.get(v.getId());
            item.put("extractStatus", vc != null ? vc.getExtractStatus() : null);
            item.put("textCategory", vc != null ? vc.getTextCategory() : null);
            item.put("qualityScore", vc != null ? vc.getQualityScore() : null);
            item.put("mood", vc != null ? vc.getMood() : null);

            // 标签摘要: 取 top 5 高权重标签
            List<VideoTag> vtList = tagMap.getOrDefault(v.getId(), List.of());
            List<Map<String, Object>> topTags = vtList.stream()
                    .sorted(Comparator.comparingDouble(t -> -(t.getWeight() != null ? t.getWeight() : 0)))
                    .limit(5)
                    .map(t -> {
                        Map<String, Object> tm = new LinkedHashMap<>();
                        tm.put("tag", t.getTag());
                        tm.put("source", t.getSource());
                        tm.put("weight", t.getWeight());
                        tm.put("confidence", t.getConfidence());
                        return tm;
                    }).toList();
            item.put("tags", topTags);
            item.put("tagCount", vtList.size());

            // 按 status 过滤
            if (extractStatus != null && !extractStatus.isBlank()) {
                String curStatus = vc != null ? String.valueOf(vc.getExtractStatus()) : "null";
                if (!extractStatus.equals(curStatus)) continue;
            }
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("items", items);
        return Result.ok(result);
    }

    private Long tryParseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1L; }
    }

    @GetMapping("/video-tags/{videoId}")
    public Result<Map<String, Object>> getVideoTags(@PathVariable Long videoId, HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        var video = videoMapper.selectById(videoId);
        if (video == null) return Result.fail("Video not found");

        // AI-generated tags from video_content
        var vc = videoContentMapper.selectById(videoId);
        List<String> autoSceneTags = new ArrayList<>();
        List<String> autoObjectTags = new ArrayList<>();
        List<String> autoKeywords = new ArrayList<>();
        String textCategory = "";

        Integer extractStatus = null;
        Integer extractTimeMs = null;
        Double qualityScore = null;
        String mood = null;
        String style = null;
        String musicGenre = null;
        Boolean hasSpeech = null;

        if (vc != null) {
            textCategory = vc.getTextCategory() != null ? vc.getTextCategory() : "";
            autoSceneTags = parseJsonArray(vc.getSceneTags());
            autoObjectTags = parseJsonArray(vc.getObjectTags());
            autoKeywords = parseJsonArray(vc.getKeywords());
            extractStatus = vc.getExtractStatus();
            extractTimeMs = vc.getExtractTimeMs();
            qualityScore = vc.getQualityScore();
            mood = vc.getMood();
            style = vc.getStyle();
            musicGenre = vc.getMusicGenre();
            hasSpeech = vc.getHasSpeech();
        }

        // Manual tags from video_tag
        List<com.douyin.entity.VideoTag> tags = videoTagMapper.selectList(
                new LambdaQueryWrapper<com.douyin.entity.VideoTag>()
                        .eq(com.douyin.entity.VideoTag::getVideoId, videoId));

        List<Map<String, Object>> manualTags = new ArrayList<>();
        List<Map<String, Object>> autoTags = new ArrayList<>();
        for (var t : tags) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("tag", t.getTag());
            item.put("source", t.getSource());
            item.put("weight", t.getWeight());
            item.put("confidence", t.getConfidence());
            item.put("signalCount", t.getSignalCount());
            item.put("lastSignal", t.getLastSignal());
            if ("manual".equals(t.getSource())) manualTags.add(item);
            else autoTags.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("videoId", videoId);
        result.put("desc", video.getDesc());
        result.put("coverUrl", getEffectiveCover(video));
        result.put("videoUrl", video.getVideoUrl());
        result.put("type", video.getType());
        result.put("textCategory", textCategory);
        result.put("sceneTags", autoSceneTags);
        result.put("objectTags", autoObjectTags);
        result.put("autoKeywords", autoKeywords);
        result.put("manualTags", manualTags);
        result.put("autoTags", autoTags);
        // v3.0 提取状态 & 特征摘要
        result.put("extractStatus", extractStatus);
        result.put("extractTimeMs", extractTimeMs);
        result.put("qualityScore", qualityScore);
        result.put("mood", mood);
        result.put("style", style);
        result.put("musicGenre", musicGenre);
        result.put("hasSpeech", hasSpeech);
        return Result.ok(result);
    }

    @PutMapping("/video-tags/{videoId}")
    public Result<?> updateVideoTags(@PathVariable Long videoId,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) body.get("tags");
        if (tags == null) return Result.fail("tags is required");

        // Remove existing manual tags
        videoTagMapper.delete(new LambdaQueryWrapper<com.douyin.entity.VideoTag>()
                .eq(com.douyin.entity.VideoTag::getVideoId, videoId)
                .eq(com.douyin.entity.VideoTag::getSource, "manual"));

        // Insert new manual tags
        for (var t : tags) {
            var tag = new com.douyin.entity.VideoTag();
            tag.setVideoId(videoId);
            tag.setTag(String.valueOf(t.get("tag")));
            tag.setSource("manual");
            Object weight = t.get("weight");
            tag.setWeight(weight instanceof Number ? ((Number) weight).doubleValue() : 0.8);
            videoTagMapper.insert(tag);
        }

        return Result.ok(Map.of("message", "Tags updated", "count", tags.size()));
    }

    // ========== Content Feature Re-extraction ==========

    /** 手动重提单个视频的特征提取 */
    @PostMapping("/re-extract/{videoId}")
    public Result<?> reExtract(@PathVariable Long videoId, HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        boolean ok = contentFeatureService.reExtract(videoId);
        return ok ? Result.ok(Map.of("message", "已加入队列", "videoId", videoId))
                : Result.fail("视频不存在");
    }

    /** 批量重提所有失败/未完成的作品 */
    @PostMapping("/re-extract-failed")
    public Result<?> reExtractAllFailed(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        int count = contentFeatureService.reExtractAllFailed();
        return Result.ok(Map.of("message", "批量重提完成", "count", count));
    }

    /** 查看特征提取队列状态 */
    @GetMapping("/extract-queue-status")
    public Result<?> extractQueueStatus(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        return Result.ok(contentFeatureService.getQueueStatus());
    }

    // ========== Dashboard Summary (comprehensive overview) ==========

    @GetMapping("/dashboard-summary")
    public Result<Map<String, Object>> dashboardSummary(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        Map<String, Object> data = new LinkedHashMap<>();

        // --- Online & Active ---
        long onlineUsers = activeSessionMapper.selectCount(
                new LambdaQueryWrapper<ActiveSession>().eq(ActiveSession::getIsActive, 1));
        long todayActiveUsers = watchHistoryMapper.selectCount(
                new LambdaQueryWrapper<WatchHistory>().ge(WatchHistory::getCreateTime, todayStart));
        data.put("onlineUsers", onlineUsers);
        data.put("todayActiveUsers", todayActiveUsers);

        // --- Content ---
        long todayNewVideos = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().ge(Video::getCreateTime, todayStart));
        long totalVideos = videoMapper.selectCount(null);
        long totalUsers = userMapper.selectCount(null);

        // Content by type
        Map<String, Long> contentTypeBreakdown = new LinkedHashMap<>();
        for (String t : List.of("recommend-video", "image", "text", "long-video")) {
            long c = videoMapper.selectCount(new LambdaQueryWrapper<Video>().eq(Video::getType, t));
            contentTypeBreakdown.put(t, c);
        }

        data.put("todayNewVideos", todayNewVideos);
        data.put("totalVideos", totalVideos);
        data.put("totalUsers", totalUsers);
        data.put("contentTypeBreakdown", contentTypeBreakdown);

        // --- Review Pipeline ---
        long pendingVideos = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().eq(Video::getStatus, "PENDING"));
        long approvedVideos = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().eq(Video::getStatus, "APPROVED"));
        long rejectedVideos = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().eq(Video::getStatus, "REJECTED"));

        Map<String, Long> reviewPipeline = new LinkedHashMap<>();
        reviewPipeline.put("pending", pendingVideos);
        reviewPipeline.put("approved", approvedVideos);
        reviewPipeline.put("rejected", rejectedVideos);
        data.put("reviewPipeline", reviewPipeline);

        // --- Today's Engagement ---
        long todayViews = watchHistoryMapper.selectCount(
                new LambdaQueryWrapper<WatchHistory>().ge(WatchHistory::getCreateTime, todayStart));
        long todayLikes = likeMapper.selectCount(
                new LambdaQueryWrapper<Like>().ge(Like::getCreateTime, todayStart));
        long todayCollects = videoCollectMapper.selectCount(
                new LambdaQueryWrapper<VideoCollect>().ge(VideoCollect::getCreateTime, todayStart));
        long todayComments = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().ge(Comment::getCreateTime, todayStart));
        long todayShares = 0; // shares are embedded in video table, count new videos today
        long todaySearches = searchHistoryMapper.selectCount(
                new LambdaQueryWrapper<SearchHistory>().ge(SearchHistory::getCreateTime, todayStart));

        // Yesterday comparison
        long yesterdayLikes = likeMapper.selectCount(
                new LambdaQueryWrapper<Like>().ge(Like::getCreateTime, yesterdayStart).lt(Like::getCreateTime, todayStart));
        long yesterdayViews = watchHistoryMapper.selectCount(
                new LambdaQueryWrapper<WatchHistory>().ge(WatchHistory::getCreateTime, yesterdayStart).lt(WatchHistory::getCreateTime, todayStart));

        data.put("todayViews", todayViews);
        data.put("todayLikes", todayLikes);
        data.put("todayCollects", todayCollects);
        data.put("todayComments", todayComments);
        data.put("todayShares", todayShares);
        data.put("todaySearches", todaySearches);
        data.put("yesterdayLikes", yesterdayLikes);
        data.put("yesterdayViews", yesterdayViews);

        // --- User Role Distribution ---
        Map<String, Long> roleDistribution = new LinkedHashMap<>();
        for (String r : List.of("ADMIN", "USER", "MERCHANT")) {
            roleDistribution.put(r, userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getRole, r)));
        }
        data.put("roleDistribution", roleDistribution);

        // --- Ecommerce Summary ---
        long totalGoods = goodsMapper.selectCount(null);
        long totalOrders = orderMapper.selectCount(null);
        long todayOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().ge(Order::getCreateTime, todayStart));

        data.put("totalGoods", totalGoods);
        data.put("totalOrders", totalOrders);
        data.put("todayOrders", todayOrders);

        // --- Live Rooms ---
        long activeLiveRooms = liveRoomMapper.selectCount(
                new LambdaQueryWrapper<LiveRoom>().eq(LiveRoom::getStatus, "LIVE"));
        data.put("activeLiveRooms", activeLiveRooms);

        // --- User Segments (from content profiles) ---
        List<UserContentProfile> allProfiles = userContentProfileMapper.selectList(null);
        Map<String, Long> segmentDist = new LinkedHashMap<>();
        Map<String, Long> userTypeDist = new LinkedHashMap<>();
        for (var cp : allProfiles) {
            String seg = cp.getUserSegment() != null ? cp.getUserSegment() : "unknown";
            segmentDist.merge(seg, 1L, Long::sum);
            String ut = cp.getUserType() != null ? cp.getUserType() : "unknown";
            userTypeDist.merge(ut, 1L, Long::sum);
        }
        data.put("userSegmentDistribution", segmentDist);
        data.put("userTypeDistribution", userTypeDist);

        // --- Top videos today (by views) ---
        List<Video> topToday = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, "APPROVED").eq(Video::getIsDelete, 0)
                        .ge(Video::getCreateTime, todayStart)
                        .orderByDesc(Video::getPlayCount)
                        .last("LIMIT 5"));
        data.put("topTodayVideos", topToday.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId()); m.put("desc", v.getDesc()); m.put("playCount", v.getPlayCount());
            m.put("likeCount", v.getLikeCount()); m.put("type", v.getType());
            return m;
        }).collect(Collectors.toList()));

        return Result.ok(data);
    }

    // ========== Content Breakdown ==========

    @GetMapping("/content-breakdown")
    public Result<Map<String, Object>> contentBreakdown(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        Map<String, Object> data = new LinkedHashMap<>();

        // By type
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String t : List.of("recommend-video", "image", "text", "long-video")) {
            byType.put(t, videoMapper.selectCount(
                    new LambdaQueryWrapper<Video>().eq(Video::getType, t)));
        }
        data.put("byType", byType);

        // By status
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "APPROVED", "REJECTED")) {
            byStatus.put(s, videoMapper.selectCount(
                    new LambdaQueryWrapper<Video>().eq(Video::getStatus, s)));
        }
        data.put("byStatus", byStatus);

        // Average duration by type
        List<Video> allVideos = videoMapper.selectList(
                new LambdaQueryWrapper<Video>().isNotNull(Video::getDuration));
        Map<String, Double> avgDurationByType = allVideos.stream()
                .filter(v -> v.getType() != null)
                .collect(Collectors.groupingBy(Video::getType,
                        Collectors.averagingDouble(Video::getDuration)));
        data.put("avgDurationByType", avgDurationByType);

        // Quality scores from video_content
        List<VideoContent> contents = videoContentMapper.selectList(
                new LambdaQueryWrapper<VideoContent>().isNotNull(VideoContent::getQualityScore));
        Map<String, Long> qualityBuckets = new LinkedHashMap<>();
        qualityBuckets.put("high (>0.7)", contents.stream().filter(v -> v.getQualityScore() > 0.7).count());
        qualityBuckets.put("medium (0.4-0.7)", contents.stream().filter(v -> v.getQualityScore() >= 0.4 && v.getQualityScore() <= 0.7).count());
        qualityBuckets.put("low (<0.4)", contents.stream().filter(v -> v.getQualityScore() < 0.4).count());
        qualityBuckets.put("unscored", videoMapper.selectCount(null) - contents.size());
        data.put("qualityDistribution", qualityBuckets);

        // Daily post trend last 14 days
        LocalDateTime since14 = LocalDate.now().minusDays(14).atStartOfDay();
        List<Video> recentVideos = videoMapper.selectList(
                new LambdaQueryWrapper<Video>().ge(Video::getCreateTime, since14));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Long> dailyPosts = recentVideos.stream()
                .collect(Collectors.groupingBy(v -> v.getCreateTime().format(fmt),
                        LinkedHashMap::new, Collectors.counting()));
        data.put("dailyPostTrend", dailyPosts);

        return Result.ok(data);
    }

    // ========== User Growth ==========

    @GetMapping("/user-growth")
    public Result<Map<String, Object>> userGrowth(
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, since));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Long> dailyRegistrations = users.stream()
                .collect(Collectors.groupingBy(u -> u.getCreateTime().format(fmt),
                        LinkedHashMap::new, Collectors.counting()));

        // Cumulative
        List<Map<String, Object>> cumulative = new ArrayList<>();
        long cum = userMapper.selectCount(
                new LambdaQueryWrapper<User>().lt(User::getCreateTime, since));
        for (var entry : dailyRegistrations.entrySet()) {
            cum += entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", entry.getKey());
            m.put("count", cum);
            cumulative.add(m);
        }

        // Active users today
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayLogins = loginHistoryMapper.selectCount(
                new LambdaQueryWrapper<LoginHistory>().ge(LoginHistory::getCreateTime, todayStart));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dailyRegistrations", dailyRegistrations);
        data.put("cumulativeGrowth", cumulative);
        data.put("todayLogins", todayLogins);
        data.put("totalUsers", userMapper.selectCount(null));
        return Result.ok(data);
    }

    // ========== Engagement Detail ==========

    @GetMapping("/engagement-detail")
    public Result<Map<String, Object>> engagementDetail(
            @RequestParam(defaultValue = "15") int days,
            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        // Likes
        Map<String, Long> likes = likeMapper.selectList(
                new LambdaQueryWrapper<Like>().ge(Like::getCreateTime, since))
                .stream().collect(Collectors.groupingBy(
                        l -> l.getCreateTime().format(fmt), LinkedHashMap::new, Collectors.counting()));

        // Collects
        Map<String, Long> collects = videoCollectMapper.selectList(
                new LambdaQueryWrapper<VideoCollect>().ge(VideoCollect::getCreateTime, since))
                .stream().collect(Collectors.groupingBy(
                        c -> c.getCreateTime().format(fmt), LinkedHashMap::new, Collectors.counting()));

        // Comments
        Map<String, Long> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().ge(Comment::getCreateTime, since))
                .stream().collect(Collectors.groupingBy(
                        c -> c.getCreateTime().format(fmt), LinkedHashMap::new, Collectors.counting()));

        // Watch history stats
        List<WatchHistory> watches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>().ge(WatchHistory::getCreateTime, since));
        Map<String, Double> avgCompletion = watches.stream()
                .filter(w -> w.getVideoDuration() != null && w.getVideoDuration() > 0)
                .collect(Collectors.groupingBy(
                        w -> w.getCreateTime().format(fmt), LinkedHashMap::new,
                        Collectors.averagingDouble(w -> Math.min(w.getWatchDuration() / w.getVideoDuration(), 1.0))));
        Map<String, Long> dailyViews = watches.stream()
                .collect(Collectors.groupingBy(
                        w -> w.getCreateTime().format(fmt), LinkedHashMap::new, Collectors.counting()));

        // Merge all
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(likes.keySet()); allKeys.addAll(collects.keySet());
        allKeys.addAll(comments.keySet()); allKeys.addAll(dailyViews.keySet());

        List<Map<String, Object>> trend = new ArrayList<>();
        for (String key : allKeys) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", key);
            item.put("likes", likes.getOrDefault(key, 0L));
            item.put("collects", collects.getOrDefault(key, 0L));
            item.put("comments", comments.getOrDefault(key, 0L));
            item.put("views", dailyViews.getOrDefault(key, 0L));
            item.put("avgCompletion", Math.round(avgCompletion.getOrDefault(key, 0.0) * 1000) / 10.0);
            trend.add(item);
        }

        // Totals
        long totalLikes = likeMapper.selectCount(null);
        long totalCollects = videoCollectMapper.selectCount(null);
        long totalComments = commentMapper.selectCount(null);
        long totalWatches = watchHistoryMapper.selectCount(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trend", trend);
        data.put("totalLikes", totalLikes);
        data.put("totalCollects", totalCollects);
        data.put("totalComments", totalComments);
        data.put("totalWatches", totalWatches);
        return Result.ok(data);
    }

    // ========== Traffic Sources ==========

    @GetMapping("/traffic-sources")
    public Result<Map<String, Object>> trafficSources(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<WatchHistory> watches = watchHistoryMapper.selectList(
                new LambdaQueryWrapper<WatchHistory>().isNotNull(WatchHistory::getTrafficSource));

        Map<String, Long> sourceDist = watches.stream()
                .collect(Collectors.groupingBy(
                        w -> w.getTrafficSource() != null ? w.getTrafficSource() : "unknown",
                        Collectors.counting()));

        // Sort by count desc
        List<Map<String, Object>> sources = sourceDist.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sources", sources);
        data.put("total", watches.size());
        return Result.ok(data);
    }

    // ========== Device Stats ==========

    @GetMapping("/device-stats")
    public Result<Map<String, Object>> deviceStats(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<ActiveSession> sessions = activeSessionMapper.selectList(
                new LambdaQueryWrapper<ActiveSession>().eq(ActiveSession::getIsActive, 1));

        Map<String, Long> osDist = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getDeviceOs() != null ? s.getDeviceOs() : "Unknown",
                        Collectors.counting()));

        Map<String, Long> browserDist = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getBrowserName() != null ? s.getBrowserName() : "Unknown",
                        Collectors.counting()));

        // Screen resolution buckets
        Map<String, Long> screenBuckets = new LinkedHashMap<>();
        for (var s : sessions) {
            if (s.getScreenWidth() != null && s.getScreenHeight() != null) {
                String key;
                if (s.getScreenWidth() >= 1920) key = ">=1920 (Full HD+)";
                else if (s.getScreenWidth() >= 1366) key = "1366-1919 (HD)";
                else if (s.getScreenWidth() >= 768) key = "768-1365 (Tablet)";
                else key = "<768 (Mobile)";
                screenBuckets.merge(key, 1L, Long::sum);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("osDistribution", osDist);
        data.put("browserDistribution", browserDist);
        data.put("screenResolution", screenBuckets);
        data.put("totalSessions", sessions.size());
        return Result.ok(data);
    }

    // ========== Ecommerce Overview ==========

    @GetMapping("/ecommerce-overview")
    public Result<Map<String, Object>> ecommerceOverview(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalGoods = goodsMapper.selectCount(null);
        long activeGoods = goodsMapper.selectCount(
                new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 1));
        long totalOrders = orderMapper.selectCount(null);
        long todayOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().ge(Order::getCreateTime, todayStart));

        // Order status breakdown
        Map<String, Long> orderStatus = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "PAID", "SHIPPED", "RECEIVED", "CANCELLED")) {
            orderStatus.put(s, orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>().eq(Order::getStatus, s)));
        }

        // Revenue from wallet transactions
        List<WalletTransaction> txns = walletTransactionMapper.selectList(
                new LambdaQueryWrapper<WalletTransaction>().ge(WalletTransaction::getCreateTime, todayStart));
        BigDecimal todayRevenue = txns.stream()
                .filter(t -> "PAY".equals(t.getType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Today's top sellers
        List<Goods> topGoods = goodsMapper.selectList(
                new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 1).eq(Goods::getIsDelete, 0)
                        .orderByDesc(Goods::getSold).last("LIMIT 5"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalGoods", totalGoods);
        data.put("activeGoods", activeGoods);
        data.put("totalOrders", totalOrders);
        data.put("todayOrders", todayOrders);
        data.put("orderStatus", orderStatus);
        data.put("todayRevenue", todayRevenue);
        data.put("topGoods", topGoods.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId()); m.put("name", g.getName());
            m.put("price", g.getRealPrice()); m.put("sold", g.getSold());
            return m;
        }).collect(Collectors.toList()));
        return Result.ok(data);
    }

    // ========== User Segments Overview ==========

    @GetMapping("/user-segments")
    public Result<Map<String, Object>> userSegments(HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        List<UserContentProfile> profiles = userContentProfileMapper.selectList(null);

        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> bySegment = new LinkedHashMap<>();
        Map<String, Double> avgRatesBySegment = new LinkedHashMap<>();

        for (var cp : profiles) {
            String type = cp.getUserType() != null ? cp.getUserType() : "unknown";
            byType.merge(type, 1L, Long::sum);
            String seg = cp.getUserSegment() != null ? cp.getUserSegment() : "unknown";
            bySegment.merge(seg, 1L, Long::sum);
        }

        // Average completion rate by segment
        Map<String, Double> avgBounceBySeg = profiles.stream()
                .filter(p -> p.getUserSegment() != null && p.getBounceRate() != null)
                .collect(Collectors.groupingBy(UserContentProfile::getUserSegment,
                        Collectors.averagingDouble(UserContentProfile::getBounceRate)));

        // Active days distribution
        Map<String, Long> activeDaysDist = new LinkedHashMap<>();
        for (var cp : profiles) {
            if (cp.getActiveDaysLastWeek() != null) {
                int days = cp.getActiveDaysLastWeek();
                String bucket = days >= 7 ? "7天" : days >= 5 ? "5-6天" : days >= 3 ? "3-4天" : "1-2天";
                activeDaysDist.merge(bucket, 1L, Long::sum);
            }
        }

        // Average watch duration by segment
        Map<String, Double> avgWatchBySeg = profiles.stream()
                .filter(p -> p.getUserSegment() != null && p.getAvgWatchDuration() != null)
                .collect(Collectors.groupingBy(UserContentProfile::getUserSegment,
                        Collectors.averagingDouble(UserContentProfile::getAvgWatchDuration)));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byUserType", byType);
        data.put("bySegment", bySegment);
        data.put("totalProfiled", profiles.size());
        data.put("avgBounceBySegment", avgBounceBySeg);
        data.put("activeDaysDistribution", activeDaysDist);
        data.put("avgWatchBySegment", avgWatchBySeg);
        return Result.ok(data);
    }

    // ========== Helper ==========

    /** coverUrl 为空时回退到 imageUrls 第一张或 videoUrl (图文/文字作品封面即首图) */
    private String getEffectiveCover(Video v) {
        if (v.getCoverUrl() != null && !v.getCoverUrl().isEmpty()) return v.getCoverUrl();
        String imageUrls = v.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<String> urls = parseJsonArray(imageUrls);
            if (!urls.isEmpty()) return urls.get(0);
        }
        return v.getVideoUrl();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) return List.of();
        try {
            // Handle JSON string array like ["food","recipe","tutorial"]
            String cleaned = json.trim();
            if (cleaned.startsWith("[")) cleaned = cleaned.substring(1);
            if (cleaned.endsWith("]")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            return Arrays.stream(cleaned.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
