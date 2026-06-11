package com.douyin.websocket;

import com.douyin.service.LiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 直播推流/拉流 WebSocket
 *   Broadcaster → ws://host/ws/live/{roomId}?role=host
 *   Viewer     → ws://host/ws/live/{roomId}?role=viewer
 *
 * 帧格式 (JSON):
 *   { "type": "frame", "data": "base64..." }
 *   { "type": "chat", "userId": 123, "nickname": "...", "text": "..." }
 *   { "type": "like", "count": 1 }
 *   { "type": "end" }
 */
@Slf4j
@Component
public class LiveStreamHandler extends TextWebSocketHandler {

    private final LiveService liveService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** roomId → Set<WebSocketSession> */
    private static final ConcurrentHashMap<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    /** sessionId → roomId */
    private static final ConcurrentHashMap<String, Long> sessionRoom = new ConcurrentHashMap<>();
    /** sessionId → role (host/viewer) */
    private static final ConcurrentHashMap<String, String> sessionRole = new ConcurrentHashMap<>();
    /** sessionId → userId (for host disconnect cleanup) */
    private static final ConcurrentHashMap<String, Long> sessionUserId = new ConcurrentHashMap<>();
    /** roomId → 延迟关播任务（主播断线 30s 后才真正关播，给重连留机会） */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> pendingEnds = new ConcurrentHashMap<>();

    /** 主播断线后延迟关播的秒数 */
    private static final int END_DELAY_SECONDS = 30;

    public LiveStreamHandler(LiveService liveService) {
        this.liveService = liveService;
    }

    public static int getViewerCount(Long roomId) {
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set == null) return 0;
        return (int) set.stream().filter(s -> "viewer".equals(sessionRole.get(s.getId()))).count();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = extractRoomId(session);
        String role = extractRole(session);
        if (roomId == null) {
            closeSession(session);
            return;
        }
        sessionRoom.put(session.getId(), roomId);
        sessionRole.put(session.getId(), role != null ? role : "viewer");

        // 记录 host 的 userId，用于断线时自动关播
        Object uid = session.getAttributes().get("userId");
        if (uid != null) {
            sessionUserId.put(session.getId(), (Long) uid);
        }

        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("Live WS connected: roomId={}, role={}, viewers={}", roomId, role, getViewerCount(roomId));

        // 主播重连 → 取消延迟关播任务
        if ("host".equals(role)) {
            ScheduledFuture<?> pending = pendingEnds.remove(roomId);
            if (pending != null) {
                pending.cancel(false);
                log.info("Host reconnected, cancelled auto-end for room {}", roomId);
            }
        }

        // 通知所有观众人数变化
        broadcastRoomStatus(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = sessionRoom.get(session.getId());
        if (roomId == null) return;

        String payload = message.getPayload();
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set == null) return;

        // 推流端: 广播帧和聊天给所有观众
        String role = sessionRole.get(session.getId());
        if ("host".equals(role)) {
            // 广播给所有 viewer
            for (WebSocketSession s : set) {
                if (s.isOpen() && !s.getId().equals(session.getId())) {
                    try {
                        s.sendMessage(new TextMessage(payload));
                    } catch (IOException ignored) {}
                }
            }
        }

        // 观众端: 聊天/点赞消息也广播给其他人
        if ("viewer".equals(role)) {
            try {
                var obj = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
                String type = obj.has("type") ? obj.get("type").asText() : "";
                if ("chat".equals(type) || "like".equals(type)) {
                    for (WebSocketSession s : set) {
                        if (s.isOpen()) {
                            try {
                                s.sendMessage(new TextMessage(payload));
                            } catch (IOException ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = sessionRoom.remove(session.getId());
        String role = sessionRole.remove(session.getId());
        Long userId = sessionUserId.remove(session.getId());

        // 主播断线 → 延迟 30s 关播，给重连留机会
        if ("host".equals(role) && roomId != null && userId != null) {
            ScheduledFuture<?> existing = pendingEnds.get(roomId);
            if (existing != null) {
                existing.cancel(false);
            }
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                pendingEnds.remove(roomId);
                try {
                    // 再次确认该房间没有活跃的 host 连接
                    Set<WebSocketSession> sessions = rooms.get(roomId);
                    boolean hostOnline = sessions != null && sessions.stream()
                            .anyMatch(s -> s.isOpen() && "host".equals(sessionRole.get(s.getId())));
                    if (!hostOnline) {
                        liveService.endLive(roomId, userId);
                        log.info("Auto-ended live room {} after {}s delay, userId={}", roomId, END_DELAY_SECONDS, userId);
                    }
                } catch (Exception e) {
                    log.error("Failed to auto-end room {}: {}", roomId, e.getMessage());
                }
            }, END_DELAY_SECONDS, TimeUnit.SECONDS);
            pendingEnds.put(roomId, future);
            log.info("Host disconnected, scheduling auto-end for room {} in {}s", roomId, END_DELAY_SECONDS);
        }

        if (roomId != null) {
            Set<WebSocketSession> set = rooms.get(roomId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) rooms.remove(roomId);
            }
            log.info("Live WS disconnected: roomId={}, viewers={}", roomId, getViewerCount(roomId));
            broadcastRoomStatus(roomId);
        }
    }

    private void broadcastRoomStatus(Long roomId) {
        int count = getViewerCount(roomId);
        String msg = "{\"type\":\"viewer_count\",\"count\":" + count + "}";
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set != null) {
            for (WebSocketSession s : set) {
                if (s.isOpen()) {
                    try { s.sendMessage(new TextMessage(msg)); } catch (IOException ignored) {}
                }
            }
        }
    }

    private Long extractRoomId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        try {
            // /ws/live/{roomId}
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRole(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query == null) return "viewer";
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "role".equals(kv[0])) return kv[1];
        }
        return "viewer";
    }

    private void closeSession(WebSocketSession session) {
        try { session.close(); } catch (IOException ignored) {}
    }
}
