package com.douyin.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.dto.MetricQueryRequest;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MetricsQueryService {

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final LikeMapper likeMapper;
    private final VideoCollectMapper videoCollectMapper;
    private final CommentMapper commentMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final OrderMapper orderMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final ActiveSessionMapper activeSessionMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final UserContentProfileMapper userContentProfileMapper;

    public MetricsQueryService(UserMapper userMapper, VideoMapper videoMapper,
                               WatchHistoryMapper watchHistoryMapper, LikeMapper likeMapper,
                               VideoCollectMapper videoCollectMapper, CommentMapper commentMapper,
                               SearchHistoryMapper searchHistoryMapper, OrderMapper orderMapper,
                               WalletTransactionMapper walletTransactionMapper,
                               ActiveSessionMapper activeSessionMapper,
                               LiveRoomMapper liveRoomMapper,
                               UserContentProfileMapper userContentProfileMapper) {
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.likeMapper = likeMapper;
        this.videoCollectMapper = videoCollectMapper;
        this.commentMapper = commentMapper;
        this.searchHistoryMapper = searchHistoryMapper;
        this.orderMapper = orderMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.activeSessionMapper = activeSessionMapper;
        this.liveRoomMapper = liveRoomMapper;
        this.userContentProfileMapper = userContentProfileMapper;
    }

    // ── Metric handler registry ──

    private interface MetricFetcher {
        List<TimeValue> fetch(LocalDateTime start, LocalDateTime end);
    }

    private Map<String, MetricFetcher> createFetchers() {
        Map<String, MetricFetcher> m = new LinkedHashMap<>();
        m.put("page_views", (s, e) -> {
            var list = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                    .ge(WatchHistory::getCreateTime, s).lt(WatchHistory::getCreateTime, e));
            return list.stream().map(w -> new TimeValue(w.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("active_users", (s, e) -> {
            var list = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                    .select(WatchHistory::getUserId)
                    .ge(WatchHistory::getCreateTime, s).lt(WatchHistory::getCreateTime, e));
            Set<Long> seen = new HashSet<>();
            List<TimeValue> result = new ArrayList<>();
            for (var w : list) {
                if (seen.add(w.getUserId())) result.add(new TimeValue(w.getCreateTime(), 1L));
            }
            return result;
        });
        m.put("new_users", (s, e) -> {
            var list = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .ge(User::getCreateTime, s).lt(User::getCreateTime, e));
            return list.stream().map(u -> new TimeValue(u.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("new_videos", (s, e) -> {
            var list = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                    .ge(Video::getCreateTime, s).lt(Video::getCreateTime, e));
            return list.stream().map(v -> new TimeValue(v.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("likes", (s, e) -> {
            var list = likeMapper.selectList(new LambdaQueryWrapper<Like>()
                    .ge(Like::getCreateTime, s).lt(Like::getCreateTime, e));
            return list.stream().map(l -> new TimeValue(l.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("collects", (s, e) -> {
            var list = videoCollectMapper.selectList(new LambdaQueryWrapper<VideoCollect>()
                    .ge(VideoCollect::getCreateTime, s).lt(VideoCollect::getCreateTime, e));
            return list.stream().map(c -> new TimeValue(c.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("comments", (s, e) -> {
            var list = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .ge(Comment::getCreateTime, s).lt(Comment::getCreateTime, e));
            return list.stream().map(c -> new TimeValue(c.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("searches", (s, e) -> {
            var list = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                    .ge(SearchHistory::getCreateTime, s).lt(SearchHistory::getCreateTime, e));
            return list.stream().map(h -> new TimeValue(h.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("orders", (s, e) -> {
            var list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreateTime, s).lt(Order::getCreateTime, e));
            return list.stream().map(o -> new TimeValue(o.getCreateTime(), 1L)).collect(Collectors.toList());
        });
        m.put("revenue", (s, e) -> {
            var list = walletTransactionMapper.selectList(new LambdaQueryWrapper<WalletTransaction>()
                    .eq(WalletTransaction::getType, "PAY")
                    .ge(WalletTransaction::getCreateTime, s).lt(WalletTransaction::getCreateTime, e));
            return list.stream()
                    .map(t -> new TimeValue(t.getCreateTime(), t.getAmount() != null ? t.getAmount().longValue() : 0L))
                    .collect(Collectors.toList());
        });
        m.put("online_users", (s, e) -> {
            long count = activeSessionMapper.selectCount(
                    new LambdaQueryWrapper<ActiveSession>().eq(ActiveSession::getIsActive, 1));
            return List.of(new TimeValue(LocalDateTime.now(), count));
        });
        m.put("live_rooms", (s, e) -> {
            long count = liveRoomMapper.selectCount(
                    new LambdaQueryWrapper<LiveRoom>().eq(LiveRoom::getStatus, "LIVE"));
            return List.of(new TimeValue(LocalDateTime.now(), count));
        });
        return m;
    }

    // ── Main query method ──

    public Map<String, Object> query(MetricQueryRequest req) {
        LocalDateTime start = LocalDateTime.parse(req.getTimeRange().getStart(), DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime end   = LocalDateTime.parse(req.getTimeRange().getEnd(),   DateTimeFormatter.ISO_DATE_TIME);
        DateTimeFormatter intervalFmt = "1h".equals(req.getInterval())
                ? DateTimeFormatter.ofPattern("MM-dd HH:00")
                : DateTimeFormatter.ofPattern("MM-dd");

        Map<String, MetricFetcher> fetchers = createFetchers();
        List<Map<String, Object>> timeSeries = new ArrayList<>();
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();

        // Pre-build time buckets
        List<String> timeBuckets = buildTimeBuckets(start, end, req.getInterval());

        for (String metric : req.getMetrics()) {
            MetricFetcher fetcher = fetchers.get(metric);
            if (fetcher == null) continue;

            List<TimeValue> raw = fetcher.fetch(start, end);

            // Group by time interval
            Map<String, Long> grouped = raw.stream()
                    .collect(Collectors.groupingBy(
                            tv -> tv.time.format(intervalFmt),
                            LinkedHashMap::new,
                            Collectors.summingLong(tv -> tv.value)));

            // Fill time series
            for (int i = 0; i < timeBuckets.size(); i++) {
                String bucket = timeBuckets.get(i);
                Long val = grouped.getOrDefault(bucket, 0L);
                if (timeSeries.size() <= i) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("timestamp", bucket);
                    timeSeries.add(point);
                }
                timeSeries.get(i).put(metric, val);
            }

            // Summary
            long total = grouped.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> metricSummary = new LinkedHashMap<>();
            metricSummary.put("total", total);

            // Compare with previous period
            if ("previous_period".equals(req.getCompareWith())) {
                long duration = java.time.Duration.between(start, end).toMillis();
                LocalDateTime prevEnd = start;
                LocalDateTime prevStart = prevEnd.minus(java.time.Duration.ofMillis(duration));
                List<TimeValue> prevRaw = fetcher.fetch(prevStart, prevEnd);
                long prevTotal = prevRaw.stream().mapToLong(tv -> tv.value).sum();
                double trend = prevTotal > 0
                        ? Math.round((total - prevTotal) * 1000.0 / prevTotal) / 10.0
                        : 0;
                metricSummary.put("trend", trend);
            }

            summary.put(metric, metricSummary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeSeries", timeSeries);
        result.put("summary", summary);
        return result;
    }

    private List<String> buildTimeBuckets(LocalDateTime start, LocalDateTime end, String interval) {
        List<String> buckets = new ArrayList<>();
        DateTimeFormatter fmt = "1h".equals(interval)
                ? DateTimeFormatter.ofPattern("MM-dd HH:00")
                : DateTimeFormatter.ofPattern("MM-dd");
        LocalDateTime cursor = "1h".equals(interval)
                ? start.withMinute(0).withSecond(0).withNano(0)
                : start.toLocalDate().atStartOfDay();
        while (cursor.isBefore(end)) {
            buckets.add(cursor.format(fmt));
            cursor = "1h".equals(interval) ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        return buckets;
    }

    // ── Helper class ──

    private static class TimeValue {
        final LocalDateTime time;
        final long value;
        TimeValue(LocalDateTime time, long value) { this.time = time; this.value = value; }
    }
}
