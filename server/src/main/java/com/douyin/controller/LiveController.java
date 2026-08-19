package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.config.LiveMediaProperties;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.Follow;
import com.douyin.entity.LiveRoom;
import com.douyin.entity.User;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.service.LiveService;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LivePresenceService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.UserVO;
import com.douyin.websocket.LiveStreamHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveService liveService;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final JwtUtil jwtUtil;
    private final StreamingSessionManager sessionManager;
    private final StreamingEngine streamingEngine;
    private final LiveMediaProperties mediaProperties;
    private final LiveMediaTokenService mediaTokenService;
    private final LivePresenceService livePresenceService;

    public LiveController(LiveService liveService, UserMapper userMapper,
                          FollowMapper followMapper, JwtUtil jwtUtil,
                          StreamingSessionManager sessionManager,
                          StreamingEngine streamingEngine,
                          LiveMediaProperties mediaProperties,
                          LiveMediaTokenService mediaTokenService,
                          LivePresenceService livePresenceService) {
        this.liveService = liveService;
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.jwtUtil = jwtUtil;
        this.sessionManager = sessionManager;
        this.streamingEngine = streamingEngine;
        this.mediaProperties = mediaProperties;
        this.mediaTokenService = mediaTokenService;
        this.livePresenceService = livePresenceService;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try { return jwtUtil.getUserIdFromToken(token); }
            catch (Exception ignored) {}
        }
        return null;
    }

    @PostMapping("/create")
    public Result<LiveRoom> create(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        String title = body.getOrDefault("title", "直播间");
        String coverUrl = body.getOrDefault("coverUrl", "");
        LiveRoom room = liveService.createRoom(userId, title, coverUrl);
        return Result.ok(room);
    }

    @PostMapping("/{id}/start")
    public Result<LiveRoom> start(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        LiveRoom room;
        try {
            room = liveService.startLive(id, userId);
        } catch (IllegalStateException ex) {
            log.warn("Live provider unavailable for room {}: {}", id, ex.getMessage());
            return Result.fail("直播媒体服务未就绪");
        }
        if (room == null) return Result.fail("直播间不存在或无权限");
        return Result.ok(room);
    }

    @PostMapping("/{id}/end")
    public Result<LiveRoom> end(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        LiveRoom room = liveService.endLive(id, userId);
        if (room == null) return Result.fail("直播间不存在或无权限");
        LiveStreamHandler.broadcastEnd(id);
        return Result.ok(room);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest req) {
        LiveRoom room = liveService.getById(id);
        if (room == null) return Result.fail("直播间不存在");

        User host = userMapper.selectById(room.getHostUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", room.getId());
        data.put("hostUserId", room.getHostUserId());
        data.put("title", room.getTitle());
        data.put("coverUrl", room.getCoverUrl());
        data.put("status", room.getStatus());
        data.put("viewerCount", room.getViewerCount());
        data.put("totalViewers", room.getTotalViewers());
        data.put("likeCount", room.getLikeCount());
        data.put("createTime", room.getCreateTime());
        if ("LIVE".equals(room.getStatus())) {
            Long actorId = getLoginUserId(req);
            if (actorId != null) {
                Map<String, Object> media = mediaFor(room, actorId);
                data.put("media", media);
                data.put("playUrl", media.get("whepUrl"));
            }
        }

        // Native-engine stats are optional and never represent the SRS browser
        // path unless the deployment explicitly enables the real engine.
        if ("LIVE".equals(room.getStatus())) {
            var session = sessionManager.getSession(id);
            if (session != null && session.isActive()) {
                var stats = session.getStats();
                data.put("stats", Map.of(
                    "captureFps", stats.getCaptureFps(),
                    "encodeFps", stats.getEncodeFps(),
                    "currentBitrate", stats.getCurrentBitrate(),
                    "rttMs", stats.getRttMs(),
                    "packetLoss", stats.getPacketLoss(),
                    "gpuUsage", stats.getGpuUsagePercent(),
                    "uptimeMs", session.getUptimeMs()
                ));
            }
        }

        if (host != null) data.put("host", UserVO.from(host));
        return Result.ok(data);
    }

    @GetMapping("/rooms")
    public Result<PageDTO<Map<String, Object>>> rooms(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {

        LambdaQueryWrapper<LiveRoom> wrapper = new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getStatus, "LIVE")
                .orderByDesc(LiveRoom::getViewerCount)
                .orderByDesc(LiveRoom::getCreateTime);

        IPage<LiveRoom> page = liveService.page(new Page<>(pageNo, pageSize), wrapper);
        List<Long> hostIds = page.getRecords().stream()
                .map(LiveRoom::getHostUserId).distinct().toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!hostIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(hostIds).stream()
                    .collect(Collectors.toMap(User::getUid, u -> u));
        }

        Map<Long, User> finalUserMap = userMap;
        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("coverUrl", r.getCoverUrl());
            m.put("status", r.getStatus());
            m.put("viewerCount", r.getViewerCount());
            m.put("likeCount", r.getLikeCount());
            m.put("encoderType", r.getEncoderType());
            m.put("codec", r.getCodec());
            User u = finalUserMap.get(r.getHostUserId());
            if (u != null) m.put("host", UserVO.from(u));
            return m;
        }).toList();

        return Result.ok(new PageDTO<>(page.getTotal(), pageNo, pageSize, list));
    }

    @GetMapping("/featured")
    public Result<Map<String, Object>> featured(HttpServletRequest req) {
        LiveRoom room = liveService.getOne(
                new LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getStatus, "LIVE")
                        .orderByDesc(LiveRoom::getViewerCount)
                        .last("LIMIT 1"));
        if (room == null) return Result.fail("当前没有直播");
        User host = userMapper.selectById(room.getHostUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", room.getId());
        data.put("title", room.getTitle());
        data.put("coverUrl", room.getCoverUrl());
        data.put("status", room.getStatus());
        data.put("viewerCount", room.getViewerCount());
        data.put("likeCount", room.getLikeCount());
        data.put("encoderType", room.getEncoderType());
        data.put("codec", room.getCodec());
        Long actorId = getLoginUserId(req);
        if (actorId != null) data.put("media", mediaFor(room, actorId));
        if (host != null) data.put("host", UserVO.from(host));
        return Result.ok(data);
    }

    @GetMapping("/rooms/following")
    public Result<List<Map<String, Object>>> followingRooms(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        List<Long> followIds = followMapper.selectList(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .select(Follow::getFollowId)
        ).stream().map(Follow::getFollowId).toList();

        if (followIds.isEmpty()) return Result.ok(List.of());

        List<LiveRoom> rooms = liveService.list(
                new LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getStatus, "LIVE")
                        .in(LiveRoom::getHostUserId, followIds)
                        .orderByDesc(LiveRoom::getViewerCount));

        Map<Long, User> userMap = userMapper.selectBatchIds(followIds).stream()
                .collect(Collectors.toMap(User::getUid, u -> u));

        List<Map<String, Object>> list = rooms.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("coverUrl", r.getCoverUrl());
            m.put("status", r.getStatus());
            m.put("viewerCount", r.getViewerCount());
            m.put("likeCount", r.getLikeCount());
            m.put("codec", r.getCodec());
            User u = userMap.get(r.getHostUserId());
            if (u != null) m.put("host", UserVO.from(u));
            return m;
        }).toList();

        return Result.ok(list);
    }

    @PostMapping("/{id}/join")
    public Result<?> join(@PathVariable Long id,
                          @RequestBody(required = false) Map<String, String> body,
                          HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        String sessionId = normalizePresenceSession(body == null ? null : body.get("sessionId"));
        LiveRoom room = liveService.getById(id);
        if (room == null || !"LIVE".equals(room.getStatus())) return Result.fail("直播间未开播或不存在");
        if (livePresenceService.touch(id, userId, sessionId)) {
            liveService.joinRoom(id);
        }
        room = liveService.getById(id);
        if (room == null) return Result.fail("直播间未开播或不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("viewerCount", livePresenceService.count(id));
        out.put("sessionId", sessionId);
        out.put("media", mediaFor(room, userId));
        return Result.ok(out);
    }

    @PostMapping("/{id}/leave")
    public Result<?> leave(@PathVariable Long id,
                           @RequestBody(required = false) Map<String, String> body,
                           HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        String sessionId = normalizePresenceSession(body == null ? null : body.get("sessionId"));
        if (livePresenceService.leave(id, userId, sessionId)) {
            liveService.leaveRoom(id);
        }
        return Result.ok(Map.of("viewerCount", livePresenceService.count(id)));
    }

    @PostMapping("/{id}/like")
    public Result<?> like(@PathVariable Long id, HttpServletRequest req) {
        if (getLoginUserId(req) == null) return Result.fail("请先登录");
        liveService.addLike(id);
        LiveRoom room = liveService.getById(id);
        return Result.ok(Map.of("likeCount", room != null ? room.getLikeCount() : 0));
    }

    // ===== Streaming Engine Management =====

    @PutMapping("/{id}/bitrate")
    public Result<?> updateBitrate(@PathVariable Long id,
                                    @RequestBody Map<String, Integer> body,
                                    HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        LiveRoom room = liveService.getById(id);
        if (room == null || !userId.equals(room.getHostUserId())) return Result.fail("无权操作该直播间");
        int bitrate = Math.max(300_000, Math.min(body.getOrDefault("bitrate", 2_500_000), 8_000_000));
        var session = sessionManager.getSession(id);
        if (session != null) {
            session.getEngine().setBitrate(bitrate);
            return Result.ok(Map.of("bitrate", bitrate));
        }
        return Result.fail("直播间未开播");
    }

    @PutMapping("/{id}/beauty")
    public Result<?> updateBeauty(@PathVariable Long id,
                                   @RequestBody StreamingEngine.BeautyConfig config,
                                   HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        LiveRoom room = liveService.getById(id);
        if (room == null || !userId.equals(room.getHostUserId())) return Result.fail("无权操作该直播间");
        var session = sessionManager.getSession(id);
        if (session != null) {
            session.getEngine().setBeautyConfig(config);
            return Result.ok();
        }
        return Result.fail("直播间未开播");
    }

    @GetMapping("/{id}/stats")
    public Result<?> getStreamStats(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        LiveRoom room = liveService.getById(id);
        if (userId == null || room == null || !userId.equals(room.getHostUserId())) {
            return Result.fail("无权查看该直播间统计");
        }
        var session = sessionManager.getSession(id);
        if (session != null && session.isActive()) {
            return Result.ok(session.getStats());
        }
        return Result.fail("直播间未开播");
    }

    @GetMapping("/engine/status")
    public Result<Map<String, Object>> engineStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("activeSessions", sessionManager.getActiveCount());
        status.put("nativeEngine", streamingEngine.isNativeAvailable());
        status.put("activeRooms", sessionManager.getActiveRoomIds());
        return Result.ok(status);
    }

    private Map<String, Object> mediaFor(LiveRoom room, Long actorId) {
        Map<String, Object> media = new LinkedHashMap<>();
        String key = room.getSrtStreamId();
        if (key == null || key.isBlank()) return media;
        if (mediaTokenService.isEnabled() && actorId == null) return media;
        String playToken = mediaTokenService
                .issue(room.getId(), key, actorId, LiveMediaTokenService.Purpose.PLAY)
                .value();
        media.put("whepUrl", mediaProperties.whep(key, playToken));
        media.put("hlsUrl", mediaProperties.hls(key, playToken));
        media.put("httpFlvUrl", mediaProperties.httpFlv(key, playToken));
        media.put("ingestMode", "native-engine".equals(room.getEncoderType()) ? "native" : "browser-whip");
        if (actorId != null && actorId.equals(room.getHostUserId())) {
            String ingestToken = mediaTokenService
                    .issue(room.getId(), key, actorId, LiveMediaTokenService.Purpose.INGEST)
                    .value();
            media.put("whipUrl", mediaProperties.whip(key, ingestToken));
            media.put("rtmpUrl", mediaProperties.rtmp(key, ingestToken));
        }
        return media;
    }

    private String normalizePresenceSession(String value) {
        if (value == null || value.isBlank()) return "legacy-" + UUID.randomUUID();
        String normalized = value.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }
}
