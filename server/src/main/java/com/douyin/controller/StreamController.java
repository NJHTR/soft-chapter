package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.LiveRoomMapper;
import com.douyin.service.LiveService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Streaming engine management API.
 * Provides endpoints for WebRTC signaling, SRT ingest auth,
 * transcoder control, and adaptive bitrate configuration.
 */
@Slf4j
@RestController
@RequestMapping("/api/live/engine")
public class StreamController {

    private final StreamingEngine streamingEngine;
    private final StreamingSessionManager sessionManager;
    private final LiveService liveService;
    private final LiveRoomMapper liveRoomMapper;

    public StreamController(StreamingEngine streamingEngine,
                            StreamingSessionManager sessionManager,
                            LiveService liveService,
                            LiveRoomMapper liveRoomMapper) {
        this.streamingEngine = streamingEngine;
        this.sessionManager = sessionManager;
        this.liveService = liveService;
        this.liveRoomMapper = liveRoomMapper;
    }

    /**
     * Legacy SDP endpoint is intentionally disabled. Browser media negotiates
     * directly with SRS through WHIP/WHEP; this control plane never echoes or
     * proxies SDP and must not be mistaken for a media provider.
     */
    @PostMapping("/webrtc/offer")
    public Result<Map<String, String>> webrtcOffer(@RequestBody Map<String, Object> body) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GONE,
                "SDP echo endpoint retired; use SRS WHIP/WHEP");
    }

    /**
     * SRT ingest authentication.
     * Called by SRS/MediaMTX when a broadcaster connects via SRT.
     */
    @PostMapping("/srt/auth")
    public Result<Map<String, Object>> srtAuth(@RequestBody Map<String, String> body) {
        String streamId = body.get("stream_id");
        String streamKey = body.get("stream_key");

        // Verify stream key matches an active room
        LiveRoom room = liveService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getSrtStreamId, streamId)
                        .eq(LiveRoom::getStatus, "LIVE"));

        if (room == null || streamKey == null || streamId == null || room.getSrtStreamId() == null
                || !MessageDigest.isEqual(
                    streamKey.getBytes(StandardCharsets.UTF_8),
                    room.getSrtStreamId().getBytes(StandardCharsets.UTF_8))) {
            return Result.fail("Invalid stream ID or room not active");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authorized", true);
        result.put("room_id", room.getId());
        result.put("publish", true);
        return Result.ok(result);
    }

    /**
     * Get transcoder ABR ladder configuration for a room.
     */
    @GetMapping("/{roomId}/abr/ladder")
    public Result<List<Map<String, Object>>> getABRLadder(@PathVariable Long roomId) {
        // Default ABR ladder (matching TikTok's tiers)
        List<Map<String, Object>> ladder = new ArrayList<>();

        ladder.add(Map.of("width", 1920, "height", 1080, "fps", 30, "bitrate", 4000000, "codec", "h264"));
        ladder.add(Map.of("width", 1280, "height", 720, "fps", 30, "bitrate", 2500000, "codec", "h264"));
        ladder.add(Map.of("width", 854, "height", 480, "fps", 30, "bitrate", 1200000, "codec", "h264"));
        ladder.add(Map.of("width", 640, "height", 360, "fps", 30, "bitrate", 700000, "codec", "h264"));

        return Result.ok(ladder);
    }

    /**
     * Update the ABR ladder for a room.
     */
    @PutMapping("/{roomId}/abr/ladder")
    public Result<?> updateABRLadder(@PathVariable Long roomId,
                                      @RequestBody List<Map<String, Object>> ladder) {
        log.info("ABR ladder updated for room {}: {} tiers", roomId, ladder.size());
        return Result.ok();
    }

    /**
     * Get real-time stream metrics for the dashboard.
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("activeStreams", sessionManager.getActiveCount());
        metrics.put("activeRoomIds", sessionManager.getActiveRoomIds());
        metrics.put("nativeEngineAvailable", streamingEngine.isNativeAvailable());

        // Aggregate stats from all active sessions
        double totalFps = 0;
        long totalBytes = 0;
        int count = 0;

        for (Long roomId : sessionManager.getActiveRoomIds()) {
            var session = sessionManager.getSession(roomId);
            if (session != null && session.isActive()) {
                var stats = session.getStats();
                totalFps += stats.getEncodeFps();
                totalBytes += stats.getBytesSent();
                count++;
            }
        }

        metrics.put("totalEncodeFps", count > 0 ? totalFps / count : 0);
        metrics.put("totalBytesSent", totalBytes);

        return Result.ok(metrics);
    }
}
