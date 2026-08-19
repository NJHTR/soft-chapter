package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.LiveRoom;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SRS control callbacks. This endpoint admits or rejects provider sessions;
 * it never receives SDP, RTP, or encoded media frames.
 */
@Slf4j
@RestController
@RequestMapping("/api/live/provider/srs")
public class SrsCallbackController {

    private final LiveService liveService;
    private final LiveMediaTokenService mediaTokenService;

    public SrsCallbackController(LiveService liveService, LiveMediaTokenService mediaTokenService) {
        this.liveService = liveService;
        this.mediaTokenService = mediaTokenService;
    }

    @PostMapping("/on_publish")
    public ResponseEntity<Void> onPublish(@RequestParam Map<String, String> params) {
        return authorizeMedia(params, LiveMediaTokenService.Purpose.INGEST, "publish");
    }

    @PostMapping("/on_play")
    public ResponseEntity<Void> onPlay(@RequestParam Map<String, String> params) {
        return authorizeMedia(params, LiveMediaTokenService.Purpose.PLAY, "play");
    }

    @PostMapping({"/on_unpublish", "/on_stop"})
    public ResponseEntity<Void> onStop(@RequestParam Map<String, String> params) {
        if (!mediaTokenService.validateCallbackToken(params.get("callback_token"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> authorizeMedia(
            Map<String, String> params,
            LiveMediaTokenService.Purpose purpose,
            String action) {
        if (!mediaTokenService.validateCallbackToken(params.get("callback_token"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String streamKey = firstNonBlank(params.get("stream"), params.get("name"));
        String token = firstNonBlank(params.get("token"), queryParameter(params.get("param"), "token"));
        if (streamKey == null || token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        LiveRoom room = liveService.getOne(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .eq(LiveRoom::getStatus, "LIVE")
                .last("LIMIT 1"));
        if (room == null
                || !"LIVE".equals(room.getStatus())
                || !mediaTokenService.validate(token, room.getId(), streamKey, purpose)) {
            log.warn("Rejected SRS {} callback for an unknown or unauthorised stream", action);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }

    private static String queryParameter(String value, String key) {
        if (value == null || value.isBlank()) return null;
        String query = value.startsWith("?") ? value.substring(1) : value;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }
}
