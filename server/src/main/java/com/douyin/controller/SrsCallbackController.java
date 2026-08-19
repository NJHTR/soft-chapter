package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.LiveRoom;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LiveProviderSessionService;
import com.douyin.service.LiveService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
    private final LiveProviderSessionService providerSessionService;
    private final ObjectMapper objectMapper;

    public SrsCallbackController(
            LiveService liveService,
            LiveMediaTokenService mediaTokenService,
            LiveProviderSessionService providerSessionService,
            ObjectMapper objectMapper) {
        this.liveService = liveService;
        this.mediaTokenService = mediaTokenService;
        this.providerSessionService = providerSessionService;
        this.objectMapper = objectMapper;
    }

    /** Compatibility constructor for direct unit tests. */
    public SrsCallbackController(
            LiveService liveService,
            LiveMediaTokenService mediaTokenService,
            LiveProviderSessionService providerSessionService) {
        this(liveService, mediaTokenService, providerSessionService, new ObjectMapper());
    }

    /**
     * SRS sends form/query callbacks in the default profile. A raw String body
     * also lets us accept JSON without asking Spring to deserialize form data
     * into a Map (which is content-type dependent).
     */
    @PostMapping("/on_publish")
    @Transactional
    public ResponseEntity<String> onPublish(
            @RequestParam(required = false) MultiValueMap<String, String> params,
            @RequestBody(required = false) String rawBody) {
        return handlePublishOrPlay(callbackRequest(params, rawBody), LiveMediaTokenService.Purpose.INGEST,
                "on_publish");
    }

    @PostMapping("/on_play")
    @Transactional
    public ResponseEntity<String> onPlay(
            @RequestParam(required = false) MultiValueMap<String, String> params,
            @RequestBody(required = false) String rawBody) {
        return handlePublishOrPlay(callbackRequest(params, rawBody), LiveMediaTokenService.Purpose.PLAY,
                "on_play");
    }

    @PostMapping("/on_unpublish")
    @Transactional
    public ResponseEntity<String> onUnpublish(
            @RequestParam(required = false) MultiValueMap<String, String> params,
            @RequestBody(required = false) String rawBody) {
        return closeProviderSession(callbackRequest(params, rawBody), "PUBLISH", "on_unpublish", true);
    }

    @PostMapping("/on_stop")
    @Transactional
    public ResponseEntity<String> onStop(
            @RequestParam(required = false) MultiValueMap<String, String> params,
            @RequestBody(required = false) String rawBody) {
        return closeProviderSession(callbackRequest(params, rawBody), "PLAY", "on_stop", false);
    }

    /** Direct-call overload retained for contract tests without MockMvc. */
    @Transactional
    public ResponseEntity<String> onPublish(Map<String, Object> body, Map<String, String> params) {
        return handlePublishOrPlay(callbackRequest(params, body), LiveMediaTokenService.Purpose.INGEST,
                "on_publish");
    }

    /** Direct-call overload retained for contract tests without MockMvc. */
    @Transactional
    public ResponseEntity<String> onPlay(Map<String, Object> body, Map<String, String> params) {
        return handlePublishOrPlay(callbackRequest(params, body), LiveMediaTokenService.Purpose.PLAY,
                "on_play");
    }

    /** Direct-call overload retained for contract tests without MockMvc. */
    @Transactional
    public ResponseEntity<String> onUnpublish(Map<String, Object> body, Map<String, String> params) {
        return closeProviderSession(callbackRequest(params, body), "PUBLISH", "on_unpublish", true);
    }

    /** Direct-call overload retained for contract tests without MockMvc. */
    @Transactional
    public ResponseEntity<String> onStop(Map<String, Object> body, Map<String, String> params) {
        return closeProviderSession(callbackRequest(params, body), "PLAY", "on_stop", false);
    }

    private ResponseEntity<String> handlePublishOrPlay(
            CallbackRequest request,
            LiveMediaTokenService.Purpose purpose,
            String action) {
        if (!mediaTokenService.validateCallbackToken(request.callbackToken())) {
            return forbidden("missing or invalid callback token");
        }
        if (request.clientId() == null || request.clientId().isBlank()
                || request.serverId() == null || request.serverId().isBlank()) {
            return forbidden("missing provider session identity");
        }
        String streamKey = firstNonBlank(request.stream(), request.name());
        String token = firstNonBlank(request.token(), queryParameter(request.param(), "token"));
        if (streamKey == null || token == null) {
            return forbidden("missing stream key or token");
        }
        LiveRoom room = findMutableRoom(streamKey);
        if (room == null) {
            log.warn("Rejected SRS {} callback for an unknown or ended stream", action);
            return forbidden("unknown or ended room");
        }
        Optional<LiveMediaTokenService.TokenClaims> claims = resolveClaims(room, purpose, token, streamKey);
        if (claims.isEmpty()) {
            log.warn("Rejected SRS {} callback for an unauthorised stream", action);
            return forbidden("token rejected");
        }
        String providerSessionId = providerSessionId(request);
        if (purpose == LiveMediaTokenService.Purpose.INGEST) {
            if (!room.getHostUserId().equals(claims.get().userId())) {
                return forbidden("host mismatch");
            }
            if (providerSessionService.accept(
                    room.getId(), "PUBLISH", request.clientId(), request.serverId(), providerSessionId,
                    claims.get().streamKey(), claims.get().userId(), action) == null) {
                return conflict("provider session rejected");
            }
            if (liveService.providerStarted(room.getId(), claims.get().streamKey(), providerSessionId) == null) {
                providerSessionService.close(room.getId(), "PUBLISH", request.clientId(), request.serverId(),
                        providerSessionId, claims.get().streamKey(), "publish_rejected_generation");
                return conflict("provider generation is no longer current");
            }
        } else {
            if (providerSessionService.accept(
                    room.getId(), "PLAY", request.clientId(), request.serverId(), providerSessionId,
                    claims.get().streamKey(), claims.get().userId(), action) == null) {
                return conflict("provider session rejected");
            }
            // A viewer callback proves only that this viewer was authorised to
            // play. It must never revive or extend the publisher's liveness
            // window: otherwise a reconnecting viewer could keep a missing
            // stream LIVE indefinitely. Publisher liveness is owned by
            // on_publish/on_unpublish and provider reconciliation.
        }
        return ok();
    }

    private ResponseEntity<String> closeProviderSession(
            CallbackRequest request,
            String direction,
            String event,
            boolean disconnectPublisher) {
        if (!mediaTokenService.validateCallbackToken(request.callbackToken())) {
            return forbidden("missing or invalid callback token");
        }
        if (request.clientId() == null || request.clientId().isBlank()
                || request.serverId() == null || request.serverId().isBlank()) {
            return forbidden("missing provider session identity");
        }
        String streamKey = firstNonBlank(request.stream(), request.name());
        if (streamKey == null) return forbidden("missing stream key");
        LiveRoom room = findRoom(streamKey);
        if (room == null) return ok();
        String providerSessionId = providerSessionId(request);
        int closed = providerSessionService.close(room.getId(), direction, request.clientId(), request.serverId(),
                providerSessionId, streamKey, event);
        if (disconnectPublisher && closed > 0) {
            liveService.providerDisconnected(room.getId(), streamKey, providerSessionId);
        }
        // A repeated callback is intentionally a successful no-op. SRS retries
        // callbacks after network failures and must not receive a new error.
        return ok();
    }

    private Optional<LiveMediaTokenService.TokenClaims> resolveClaims(
            LiveRoom room,
            LiveMediaTokenService.Purpose purpose,
            String token,
            String streamKey) {
        Long expectedUserId = purpose == LiveMediaTokenService.Purpose.INGEST
                ? room.getHostUserId() : null;
        if (!mediaTokenService.validate(token, room.getId(), streamKey, purpose, expectedUserId)) {
            return Optional.empty();
        }
        return mediaTokenService.parse(token)
                .filter(claims -> claims.roomId().equals(room.getId()))
                .filter(claims -> streamKey.equals(claims.streamKey()))
                .filter(claims -> purpose.equals(claims.purpose()));
    }

    private LiveRoom findMutableRoom(String streamKey) {
        LiveRoom room = findRoom(streamKey);
        return room != null && isProviderMutableStatus(room.getStatus()) ? room : null;
    }

    private LiveRoom findRoom(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) return null;
        return liveService.getOne(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .last("LIMIT 1"));
    }

    private CallbackRequest callbackRequest(
            MultiValueMap<String, String> params,
            String rawBody) {
        Map<String, String> query = new LinkedHashMap<>();
        if (params != null) params.forEach((key, values) -> query.put(key, firstValue(values)));
        Map<String, Object> body = parseJsonObject(rawBody);
        return callbackRequest(query, body);
    }

    private CallbackRequest callbackRequest(Map<String, String> params, Map<String, Object> body) {
        Map<String, String> source = new LinkedHashMap<>();
        if (params != null) source.putAll(params);
        if (body != null) {
            body.forEach((key, value) -> {
                if (value != null) source.putIfAbsent(key, String.valueOf(value));
            });
        }
        return new CallbackRequest(
                source.get("action"), source.get("client_id"), source.get("server_id"),
                source.get("stream"), source.get("name"), source.get("token"), source.get("param"),
                source.get("callback_token"));
    }

    private Map<String, Object> parseJsonObject(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() { });
        } catch (IOException | RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private static String providerSessionId(CallbackRequest request) {
        return request.serverId().trim() + ":" + request.clientId().trim();
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

    private static String firstValue(java.util.List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static ResponseEntity<String> ok() {
        return ResponseEntity.ok("0");
    }

    private static ResponseEntity<String> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message);
    }

    private static ResponseEntity<String> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private static boolean isProviderMutableStatus(String status) {
        return "STARTING".equals(status) || "LIVE".equals(status) || "DEGRADED".equals(status);
    }

    private record CallbackRequest(
            String action,
            String clientId,
            String serverId,
            String stream,
            String name,
            String token,
            String param,
            String callbackToken) {
    }
}
