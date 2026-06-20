package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 搜索智能建议服务: 前缀联想 + 语义相似度 + 协同搜索 + 趋势关键词
 *
 * 两个核心接口:
 * - suggest(): 搜索框输入联想 (异步 debounce 调用)
 * - searchHints(): 视频页"猜你想搜" (当前视频 → 其他人搜什么)
 */
@Slf4j
@Service
public class SearchSuggestionService {

    private final VideoMapper videoMapper;
    private final VideoContentMapper contentMapper;
    private final UserContentProfileMapper profileMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final LikeMapper likeMapper;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public SearchSuggestionService(VideoMapper videoMapper,
                                   VideoContentMapper contentMapper,
                                   UserContentProfileMapper profileMapper,
                                   WatchHistoryMapper watchHistoryMapper,
                                   LikeMapper likeMapper,
                                   UserMapper userMapper,
                                   SearchService searchService) {
        this.videoMapper = videoMapper;
        this.contentMapper = contentMapper;
        this.profileMapper = profileMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.likeMapper = likeMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 搜索输入联想: 用户输入前缀 → 返回建议词条
     *
     * 四路召回 + 加权排序:
     * - 前缀匹配 (0.30): 视频描述/标题中包含该前缀 → 提取关键词作为建议
     * - 语义相似 (0.30): 用户搜索历史 + 视频描述语义向量相似度
     * - 场景上下文 (0.20): 当前视频相关词条 + 用户画像匹配
     * - 搜索频率 (0.20): 全局搜索频率 + 用户自身频率
     */
    public SuggestionResult suggest(String prefix, Long userId, Long currentVideoId, int limit) {
        if (prefix == null || prefix.trim().length() < 1) {
            return new SuggestionResult(List.of(), List.of());
        }

        String q = prefix.trim().toLowerCase();
        UserContentProfile profile = userId != null ? profileMapper.selectById(userId) : null;
        List<Long> userSearchVideoIds = getSearchVideoIds(profile);

        // 1. 前缀匹配: 从视频描述中提取匹配词
        List<SuggestionItem> prefixMatches = prefixMatch(q, userSearchVideoIds, limit * 3);

        // 2. 语义相似: 用户搜索历史 + 视频描述向量
        List<SuggestionItem> semanticMatches = semanticMatch(q, profile, userSearchVideoIds, limit * 3);

        // 3. 场景上下文: 当前视频关联词 + 协同搜索
        List<SuggestionItem> contextMatches = contextMatch(q, currentVideoId, userId, limit * 2);

        // 4. 搜索频率: 高频搜索词
        List<SuggestionItem> freqMatches = frequencyMatch(q, profile, limit * 2);

        // 合并去重 + 加权排序
        Map<String, SuggestionItem> merged = new LinkedHashMap<>();
        for (SuggestionItem item : prefixMatches) {
            merged.merge(item.text(), item, (a, b) -> a.score() >= b.score() ? a : b);
        }
        for (SuggestionItem item : semanticMatches) {
            merged.merge(item.text(), item, (a, b) -> a.score() >= b.score() ? a : b);
        }
        for (SuggestionItem item : contextMatches) {
            merged.merge(item.text(), item, (a, b) -> a.score() >= b.score() ? a : b);
        }
        for (SuggestionItem item : freqMatches) {
            merged.merge(item.text(), item, (a, b) -> a.score() >= b.score() ? a : b);
        }

        List<SuggestionItem> suggestions = merged.values().stream()
                .sorted(Comparator.comparingDouble(SuggestionItem::score).reversed())
                .limit(limit)
                .toList();

        // 热门搜索词 (不受前缀限制)
        List<String> hotQueries = getHotQueries(profile, 5);

        return new SuggestionResult(suggestions, hotQueries);
    }

    /**
     * "猜你想搜": 基于当前视频，推荐搜索词条。
     *
     * 三路信号:
     * - 协同搜索: 看过该视频的人最近在搜什么
     * - 内容关键词: 视频本身的标签/分类/关键词
     * - 趋势唤醒: 该品类当前热词
     */
    public List<SuggestionItem> searchHints(Long videoId, Long userId, int limit) {
        if (videoId == null) return List.of();

        Video video = videoMapper.selectById(videoId);
        VideoContent vc = contentMapper.selectById(videoId);

        Map<String, Double> scoredHints = new LinkedHashMap<>();

        // 1. 协同搜索: 最近看过该视频的用户在搜什么
        List<Long> recentWatchers = watchHistoryMapper.findRecentWatchersOfVideo(
                videoId, LocalDateTime.now().minusDays(7), 100);
        if (!recentWatchers.isEmpty()) {
            // 排除本人
            if (userId != null) recentWatchers.remove(userId);
            Map<String, Integer> coSearchFreq = new HashMap<>();
            try {
                profileMapper.findSearchQueriesByUserIds(recentWatchers)
                        .forEach(row -> {
                            try {
                                String queriesJson = (String) row.get("recent_search_queries");
                                if (queriesJson == null) return;
                                List<String> queries = objectMapper.readValue(queriesJson,
                                        new TypeReference<List<String>>() {});
                                for (String q : queries) {
                                    if (q != null && q.length() >= 2) {
                                        coSearchFreq.merge(q.toLowerCase(), 1, Integer::sum);
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}

            coSearchFreq.forEach((keyword, cnt) -> {
                double score = clamp(Math.min(0.8, cnt * 0.08), 0, 1);
                scoredHints.merge(keyword, score, Double::max);
            });
        }

        // 2. 内容关键词: 视频标签/分类/描述
        if (vc != null) {
            List<String> keywords = parseJsonList(vc.getKeywords());
            for (String kw : keywords) {
                if (kw != null && kw.length() >= 2) {
                    scoredHints.merge(kw, 0.5, Double::max);
                }
            }
            if (vc.getTextCategory() != null && vc.getTextCategory().length() >= 2) {
                scoredHints.merge(vc.getTextCategory(), 0.4, Double::max);
            }
        }
        if (video != null && video.getDesc() != null) {
            // 从描述中提取 2-4 字短语
            String desc = video.getDesc().replaceAll("[，。！？\\s]+", ",");
            for (String part : desc.split(",")) {
                String trimmed = part.trim();
                if (trimmed.length() >= 2 && trimmed.length() <= 10) {
                    scoredHints.merge(trimmed, 0.35, Double::max);
                }
            }
        }

        // 3. 趋势唤醒: 该品类当前热词 (简化: 用视频关键词搜索近7天热门同类视频)
        if (vc != null && vc.getTextCategory() != null) {
            String cat = vc.getTextCategory();
            scoredHints.merge(cat, 0.3, Double::max);
        }

        return scoredHints.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new SuggestionItem(e.getKey(), e.getValue(), 0,
                        e.getValue() >= 0.5 ? "collaborative" : "content"))
                .toList();
    }

    // ===================== 四路召回 =====================

    /** 前缀匹配: 视频描述包含前缀 */
    private List<SuggestionItem> prefixMatch(String q, List<Long> userSearchVideoIds, int limit) {
        try {
            // 从视频描述中搜索包含该前缀的记录
            List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                    .like(Video::getDesc, q)
                    .eq(Video::getStatus, "APPROVED")
                    .orderByDesc(Video::getLikeCount)
                    .last("LIMIT " + limit));
            if (videos.isEmpty()) return List.of();

            Map<String, Double> scored = new LinkedHashMap<>();
            for (Video v : videos) {
                if (v.getDesc() == null) continue;
                String desc = v.getDesc().toLowerCase();
                int idx = desc.indexOf(q);
                if (idx < 0) continue;

                // 提取包含前缀的词/短语
                int start = Math.max(0, idx - 2);
                int end = Math.min(desc.length(), idx + q.length() + 12);
                String snippet = desc.substring(start, end).trim();
                // 切出以 q 开头的完整词
                String[] words = snippet.split("[\\s,，。！？、]+");
                for (String w : words) {
                    String wt = w.trim().toLowerCase();
                    if (wt.startsWith(q) && wt.length() >= 2 && wt.length() <= 20
                            && !wt.equals(q)) {
                        double score = 0.3 + 0.05 * Math.min(5,
                                (v.getLikeCount() != null ? v.getLikeCount() : 0) / 100);
                        scored.merge(wt, clamp(score, 0, 1), Double::max);
                    }
                }
            }
            return scored.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(limit)
                    .map(e -> new SuggestionItem(e.getKey(), e.getValue(), 0, "prefix"))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 语义匹配: 用户搜索历史 + 视频向量相似 */
    private List<SuggestionItem> semanticMatch(String q, UserContentProfile profile,
                                               List<Long> userSearchVideoIds, int limit) {
        Map<String, Double> scored = new LinkedHashMap<>();

        // 用户搜索历史匹配
        if (profile != null) {
            List<String> history = parseJsonList(profile.getRecentSearchQueries());
            for (String hq : history) {
                if (hq == null) continue;
                String hql = hq.toLowerCase();
                if (hql.contains(q) || q.contains(hql)) {
                    scored.merge(hq, 0.5 + 0.1 * Math.min(3, overlap(hql, q)),
                            Double::max);
                }
            }
        }

        // 搜索结果视频的描述关键词
        if (!userSearchVideoIds.isEmpty()) {
            try {
                List<Video> searchVideos = videoMapper.selectBatchIds(
                        userSearchVideoIds.stream().limit(20).toList());
                for (Video v : searchVideos) {
                    if (v.getDesc() == null) continue;
                    String desc = v.getDesc().toLowerCase();
                    if (desc.contains(q)) {
                        for (String word : desc.split("[\\s,，。！？、]+")) {
                            String wt = word.trim().toLowerCase();
                            if (wt.contains(q) && wt.length() >= 2 && wt.length() <= 20) {
                                scored.merge(wt, 0.35, Double::max);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 同类视频的描述
        try {
            List<VideoContent> allContent = contentMapper.selectList(
                    new LambdaQueryWrapper<VideoContent>()
                            .like(VideoContent::getKeywords, q)
                            .last("LIMIT " + limit));
            for (VideoContent vc : allContent) {
                List<String> kws = parseJsonList(vc.getKeywords());
                for (String kw : kws) {
                    if (kw != null && kw.toLowerCase().contains(q) && kw.length() >= 2) {
                        scored.merge(kw, 0.3, Double::max);
                    }
                }
            }
        } catch (Exception ignored) {}

        return scored.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new SuggestionItem(e.getKey(), e.getValue(), 0, "semantic"))
                .toList();
    }

    /** 场景上下文: 当前视频相关 + 协同搜索 */
    private List<SuggestionItem> contextMatch(String q, Long currentVideoId,
                                              Long userId, int limit) {
        Map<String, Double> scored = new LinkedHashMap<>();

        if (currentVideoId != null) {
            // 当前视频的关键词
            VideoContent vc = contentMapper.selectById(currentVideoId);
            if (vc != null) {
                List<String> kws = parseJsonList(vc.getKeywords());
                for (String kw : kws) {
                    if (kw != null && kw.toLowerCase().contains(q) && kw.length() >= 2) {
                        scored.merge(kw, 0.45, Double::max);
                    }
                }
                if (vc.getTextCategory() != null
                        && vc.getTextCategory().toLowerCase().contains(q)) {
                    scored.merge(vc.getTextCategory(), 0.4, Double::max);
                }
            }

            // 协同搜索: 看过此视频的人在搜什么
            try {
                List<Long> watchers = watchHistoryMapper.findRecentWatchersOfVideo(
                        currentVideoId, LocalDateTime.now().minusDays(7), 50);
                if (userId != null) watchers.remove(userId);
                if (!watchers.isEmpty()) {
                    profileMapper.findSearchQueriesByUserIds(watchers)
                            .forEach(row -> {
                                try {
                                    String json = (String) row.get("recent_search_queries");
                                    if (json == null) return;
                                    List<String> queries = objectMapper.readValue(json,
                                            new TypeReference<List<String>>() {});
                                    for (String sq : queries) {
                                        if (sq != null && sq.toLowerCase().contains(q)
                                                && sq.length() >= 2) {
                                            scored.merge(sq, 0.5, Double::max);
                                        }
                                    }
                                } catch (Exception ignored) {}
                            });
                }
            } catch (Exception ignored) {}
        }

        return scored.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new SuggestionItem(e.getKey(), e.getValue(), 0, "context"))
                .toList();
    }

    /** 搜索频率: 全局 + 个人高频搜索词 */
    private List<SuggestionItem> frequencyMatch(String q, UserContentProfile profile, int limit) {
        Map<String, Integer> globalFreq = new HashMap<>();
        try {
            List<UserContentProfile> recentProfiles = profileMapper.selectList(
                    new LambdaQueryWrapper<UserContentProfile>()
                            .ge(UserContentProfile::getUpdateTime,
                                    LocalDateTime.now().minusDays(3))
                            .isNotNull(UserContentProfile::getRecentSearchQueries)
                            .last("LIMIT 200"));
            for (UserContentProfile p : recentProfiles) {
                List<String> queries = parseJsonList(p.getRecentSearchQueries());
                for (String sq : queries) {
                    if (sq != null && sq.toLowerCase().contains(q) && sq.length() >= 2) {
                        globalFreq.merge(sq.toLowerCase(), 1, Integer::sum);
                    }
                }
            }
        } catch (Exception ignored) {}

        Map<String, Double> scored = new LinkedHashMap<>();
        globalFreq.forEach((k, cnt) -> {
            scored.merge(k, clamp(0.2 + cnt * 0.04, 0, 0.6), Double::max);
        });

        return scored.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new SuggestionItem(e.getKey(), e.getValue(), cntToNum(globalFreq.get(e.getKey())), "frequency"))
                .toList();
    }

    // ===================== 工具方法 =====================

    private List<Long> getSearchVideoIds(UserContentProfile profile) {
        if (profile == null) return List.of();
        try {
            return likeMapper.findRecentLikedVideoIds(profile.getUserId(), 50);
        } catch (Exception e) {
            log.warn("获取用户赞过视频失败: userId={}", profile.getUserId(), e);
            return List.of();
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> getHotQueries(UserContentProfile profile, int topN) {
        // 合并趋势关键词 + 用户历史高频词
        Set<String> hot = new LinkedHashSet<>();
        if (profile != null) {
            List<String> history = parseJsonList(profile.getRecentSearchQueries());
            history.stream().limit(topN / 2).forEach(hot::add);
        }
        // 从全局趋势补充
        try {
            profileMapper.selectList(new LambdaQueryWrapper<UserContentProfile>()
                    .ge(UserContentProfile::getUpdateTime, LocalDateTime.now().minusDays(3))
                    .isNotNull(UserContentProfile::getRecentSearchQueries)
                    .last("LIMIT 50"))
                    .forEach(p -> {
                        parseJsonList(p.getRecentSearchQueries()).stream()
                                .limit(2).forEach(hot::add);
                    });
        } catch (Exception ignored) {}

        return hot.stream().limit(topN).toList();
    }

    private int overlap(String a, String b) {
        int count = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) count++;
        }
        return count;
    }

    private int cntToNum(Integer cnt) {
        return cnt != null ? cnt : 0;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ===================== 公开榜单方法 =====================

    /** "猜你想搜" 默认推荐词条：从全局趋势 + 用户历史 + 热门视频标签混合 */
    public List<Map<String, Object>> guessYouWant(Long userId, int limit) {
        UserContentProfile profile = userId != null ? profileMapper.selectById(userId) : null;
        List<String> hotQueries = getHotQueries(profile, limit);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < hotQueries.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", hotQueries.get(i));
            item.put("type", i < 3 ? 1 : -1); // 前3个标记为"新"
            result.add(item);
        }
        return result;
    }

    /** 搜索热榜: 统计近7天全站搜索词频率，排重后返回 */
    public List<Map<String, Object>> hotRank(int limit) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        try {
            List<UserContentProfile> profiles = profileMapper.selectList(
                    new LambdaQueryWrapper<UserContentProfile>()
                            .ge(UserContentProfile::getUpdateTime, LocalDateTime.now().minusDays(7))
                            .isNotNull(UserContentProfile::getRecentSearchQueries)
                            .last("LIMIT 500"));
            for (UserContentProfile p : profiles) {
                List<String> queries = parseJsonList(p.getRecentSearchQueries());
                for (String q : queries) {
                    if (q != null && q.length() >= 2 && q.length() <= 40) {
                        freq.merge(q.trim(), 1, Integer::sum);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 用视频描述热度补充（热门视频的描述关键词也能反映趋势）
        try {
            List<Video> hotVideos = videoMapper.selectList(
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getStatus, "APPROVED")
                            .orderByDesc(Video::getLikeCount)
                            .last("LIMIT 100"));
            for (Video v : hotVideos) {
                if (v.getDesc() == null) continue;
                String desc = v.getDesc().trim();
                if (desc.length() >= 2 && desc.length() <= 30) {
                    freq.merge(desc, freq.getOrDefault(desc, 0) + 3, Integer::sum);
                }
            }
        } catch (Exception ignored) {}

        AtomicInteger rank = new AtomicInteger(1);
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("rank", rank.getAndIncrement());
                    item.put("name", e.getKey());
                    item.put("heat", e.getValue());
                    return item;
                })
                .toList();
    }

    // ===================== Python 摘要常驻进程 =====================

    private final Object daemonLock = new Object();
    private volatile boolean daemonReady = false;
    private volatile Process summaryDaemon;
    private volatile BufferedWriter daemonStdin;
    private volatile BufferedReader daemonStdout;

    /** AI 智能总结: 查询真实搜索结果作为上下文, 通过常驻 Python 进程生成, 不可用时回退到模板 */
    public String generateSummary(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return "";
        String kw = keyword.trim();

        try {
            // 1. 查询真实搜索结果作为上下文
            Map<String, Object> context = buildSearchContext(kw);

            // 2. 转 JSON 发送给 Python daemon (失败重试一次)
            String contextJson = objectMapper.writeValueAsString(context);
            for (int attempt = 0; attempt < 2; attempt++) {
                String summary = queryDaemon(contextJson);
                if (summary != null && !summary.isEmpty()) return summary;
                log.warn("Python 返回空结果, 尝试 {} / 2", attempt + 1);
                // 强制重建 daemon
                destroyDaemon();
            }
        } catch (Exception e) {
            log.warn("AI 摘要失败, 回退模板: {}", e.getMessage());
        }

        return fallbackSummary(kw);
    }

    /** 构建搜索上下文: 视频列表 + 用户列表 + 分类分布 */
    private Map<String, Object> buildSearchContext(String keyword) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("keyword", keyword);

        // 视频结果
        try {
            List<Video> videos = searchService.search(keyword, 15);
            List<Map<String, Object>> videoList = new ArrayList<>();
            Map<String, Integer> catCount = new LinkedHashMap<>();

            for (int i = 0; i < Math.min(videos.size(), 15); i++) {
                Video v = videos.get(i);
                Map<String, Object> vi = new LinkedHashMap<>();
                vi.put("title", v.getDesc() != null ? v.getDesc() : "");
                vi.put("type", v.getType() != null ? v.getType() : "video");
                vi.put("likes", v.getLikeCount() != null ? v.getLikeCount() : 0);

                // 尝试获取特征标签
                VideoContent vc = contentMapper.selectById(v.getId());
                if (vc != null) {
                    vi.put("category", vc.getTextCategory() != null ? vc.getTextCategory() : "");
                    List<String> kws = parseJsonList(vc.getKeywords());
                    vi.put("keywords", kws.size() > 5 ? kws.subList(0, 5) : kws);
                    String cat = vc.getTextCategory();
                    if (cat != null && !cat.isEmpty()) {
                        catCount.merge(cat, 1, Integer::sum);
                    }
                } else {
                    vi.put("category", "");
                    vi.put("keywords", List.of());
                }
                videoList.add(vi);
            }
            ctx.put("videos", videoList);
            ctx.put("totalVideos", videos.size());

            // 前 3 品类
            String topCat = catCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining("、"));
            ctx.put("topCategories", topCat.isEmpty() ? "综合" : topCat);
        } catch (Exception e) {
            log.warn("查询视频上下文失败: {}", e.getMessage());
            ctx.put("videos", List.of());
            ctx.put("totalVideos", 0);
            ctx.put("topCategories", "");
        }

        // 用户结果
        try {
            List<User> users = userMapper.searchByKeyword(keyword);
            List<Map<String, Object>> userList = new ArrayList<>();
            for (int i = 0; i < Math.min(users.size(), 5); i++) {
                User u = users.get(i);
                Map<String, Object> ui = new LinkedHashMap<>();
                ui.put("name", u.getNickname() != null ? u.getNickname() : "");
                ui.put("followerCount", u.getFollowerCount() != null ? u.getFollowerCount() : 0);
                userList.add(ui);
            }
            ctx.put("users", userList);
            ctx.put("totalUsers", users.size());
        } catch (Exception e) {
            log.warn("查询用户上下文失败: {}", e.getMessage());
            ctx.put("users", List.of());
            ctx.put("totalUsers", 0);
        }

        return ctx;
    }

    /** 向 Python 常驻进程发送关键词并读取结果 */
    private synchronized String queryDaemon(String keyword) throws Exception {
        ensureDaemonRunning();

        daemonStdin.write(keyword + "\n");
        daemonStdin.flush();

        String line = daemonStdout.readLine();
        if (line == null) {
            log.warn("Python 进程意外退出, 尝试重启");
            destroyDaemon();
            return null;
        }

        if (line.startsWith("SUMMARY:")) {
            return line.substring(8).trim();
        } else if (line.startsWith("ERROR:")) {
            log.warn("Python 返回错误: {}", line);
            return null;
        }

        // 可能是旧版输出 (没有 SUMMARY 前缀), 直接返回
        log.info("Python 原始输出: {}", line);
        return line.trim();
    }

    /**
     * 确保 Python 摘要进程已就绪。使用 daemonLock + daemonReady 双重保护:
     * - 同一时刻只有一个线程执行启动流程 (避免重复创建进程)
     * - 启动期间其他线程阻塞等待, 不会抢占 stdin/stdout
     */
    private void ensureDaemonRunning() throws Exception {
        // 快速路径: daemon 已就绪, 无需加锁
        if (daemonReady && summaryDaemon != null && summaryDaemon.isAlive()) return;

        synchronized (daemonLock) {
            // 双重检查: 可能在等待锁期间已被其他线程启动
            if (daemonReady && summaryDaemon != null && summaryDaemon.isAlive()) return;

            // 清理残留状态
            destroyDaemonUnderLock();
            daemonReady = false;

            String pythonDir = findPythonDir();
            String python = findPython(pythonDir);

            if (tryStartDistilledModel(python, pythonDir)) {
                log.info("AI 摘要常驻进程就绪 (蒸馏模型)");
            } else {
                log.info("蒸馏模型不可用, 回退到 3B 基础模型");
                startBaseModel(python, pythonDir);
            }

            daemonReady = true;
            daemonLock.notifyAll();
        }
    }

    /** 尝试启动蒸馏模型 summary_service.py */
    private boolean tryStartDistilledModel(String python, String pythonDir) {
        try {
            log.info("尝试启动蒸馏模型...");
            ProcessBuilder pb = new ProcessBuilder(python, "-m", "distill.summary_service");
            pb.directory(new java.io.File(pythonDir));
            pb.redirectErrorStream(false);
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            summaryDaemon = pb.start();
            daemonStdin = new BufferedWriter(
                    new OutputStreamWriter(summaryDaemon.getOutputStream(), StandardCharsets.UTF_8));
            daemonStdout = new BufferedReader(
                    new InputStreamReader(summaryDaemon.getInputStream(), StandardCharsets.UTF_8));

            startStderrReader("distill-summary");

            String ready = daemonStdout.readLine();
            if ("READY".equals(ready)) return true;

            log.warn("蒸馏模型返回异常信号: {}", ready);
            destroyDaemon();
        } catch (Exception e) {
            log.warn("蒸馏模型启动失败: {}", e.getMessage());
            destroyDaemon();
        }
        return false;
    }

    /** 启动旧版 3B 基础模型 search_summary.py */
    private void startBaseModel(String python, String pythonDir) throws Exception {
        String script = pythonDir + "/search_summary.py";
        log.info("启动基础模型: {}", script);
        ProcessBuilder pb = new ProcessBuilder(python, script, "--serve");
        pb.directory(new java.io.File(pythonDir));
        pb.redirectErrorStream(false);
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        summaryDaemon = pb.start();
        daemonStdin = new BufferedWriter(
                new OutputStreamWriter(summaryDaemon.getOutputStream(), StandardCharsets.UTF_8));
        daemonStdout = new BufferedReader(
                new InputStreamReader(summaryDaemon.getInputStream(), StandardCharsets.UTF_8));

        startStderrReader("base-summary");

        String ready = daemonStdout.readLine();
        if (!"READY".equals(ready)) {
            destroyDaemon();
            throw new Exception("基础模型启动异常: " + ready);
        }
        log.info("AI 摘要常驻进程就绪 (基础模型)");
    }

    private void startStderrReader(String name) {
        new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(summaryDaemon.getErrorStream(), StandardCharsets.UTF_8))) {
                String errLine;
                while ((errLine = errReader.readLine()) != null) {
                    log.info("[Python-{}] {}", name, errLine);
                }
            } catch (Exception ignored) {}
        }, "python-" + name + "-stderr").start();
    }

    /** 销毁 daemon (外部调用, 加锁保护) */
    private void destroyDaemon() {
        synchronized (daemonLock) {
            destroyDaemonUnderLock();
            daemonReady = false;
        }
    }

    /** 销毁 daemon (调用方已持有 daemonLock) */
    private void destroyDaemonUnderLock() {
        try { if (daemonStdin != null) { daemonStdin.write("EXIT\n"); daemonStdin.flush(); } } catch (Exception ignored) {}
        try { if (daemonStdin != null) daemonStdin.close(); } catch (Exception ignored) {}
        try { if (daemonStdout != null) daemonStdout.close(); } catch (Exception ignored) {}
        try { if (summaryDaemon != null) summaryDaemon.destroyForcibly(); } catch (Exception ignored) {}
        summaryDaemon = null;
        daemonStdin = null;
        daemonStdout = null;
    }

    /** 定位 python 目录 */
    private String findPythonDir() {
        String userDir = System.getProperty("user.dir");
        String[] candidates = {
                userDir + "/python",
                userDir + "/server/python",
                new java.io.File("../python").getAbsolutePath()
        };
        for (String dir : candidates) {
            java.io.File f = new java.io.File(dir);
            if (f.isDirectory() && new java.io.File(dir + "/search_summary.py").exists()) {
                return f.getAbsolutePath();
            }
        }
        return userDir + "/server/python";
    }

    /** 查找 Python 解释器: 优先 venv */
    private String findPython(String pythonDir) {
        String uvPython = pythonDir + "/.venv/Scripts/python.exe";
        if (new java.io.File(uvPython).exists()) return uvPython;
        for (String candidate : new String[]{"python3", "python", "py"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                if (pb.start().waitFor() == 0) return candidate;
            } catch (Exception ignored) {}
        }
        return "python";
    }

    private String fallbackSummary(String kw) {
        StringBuilder sb = new StringBuilder();
        sb.append("关于「").append(kw).append("」的相关搜索结果如下：\n\n");

        sb.append("根据你的搜索关键词，系统从SeekFlow平台中匹配了相关内容。");
        sb.append("以下是为你整理的信息摘要：\n\n");

        sb.append("搜索分析：\n");
        sb.append("  关键词「").append(kw).append("」在全站内容中的相关度较高\n");
        sb.append("  已匹配到视频、图文、用户等多个维度的结果\n");
        sb.append("  根据热度排序为你呈现最优质的内容\n\n");

        sb.append("推荐建议：\n");
        sb.append("  你可以通过顶部Tab切换不同内容类型\n");
        sb.append("  「综合」标签展示所有类型的搜索结果\n");
        sb.append("  「视频」和「图文」标签仅展示对应格式内容\n");
        sb.append("  「用户」标签可查找相关创作者\n\n");

        sb.append("数据来源：\n");
        sb.append("  搜索范围覆盖全站已审核通过的公开内容\n");
        sb.append("  结果按相关度和热度综合排序\n");
        sb.append("  你可以通过关注创作者获取更多相关内容");

        return sb.toString();
    }

    // ===================== 生命周期 =====================

    /** 服务启动时预加载 Python 摘要进程，避免首次搜索等待 */
    @PostConstruct
    public void init() {
        new Thread(() -> {
            try {
                log.info("预加载 AI 摘要进程...");
                ensureDaemonRunning();
                log.info("AI 摘要进程预加载完成");
            } catch (Exception e) {
                log.warn("AI 摘要进程预加载失败 (首次搜索时重试): {}", e.getMessage());
            }
        }, "summary-preload").start();
    }

    @PreDestroy
    public void shutdown() {
        destroyDaemon();
    }

    // ===================== 内部类 =====================

    public record SuggestionItem(String text, double score, int resultCount, String source) {}

    public record SuggestionResult(List<SuggestionItem> suggestions, List<String> hotQueries) {}
}
