package com.douyin.websocket;

import com.douyin.service.LiveService;
import com.douyin.entity.LiveRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 直播控制 WebSocket（媒体不经过此通道）
 *   Broadcaster → ws://host/ws/live/{roomId}?role=host
 *   Viewer     → ws://host/ws/live/{roomId}?role=viewer
 *
 * 控制消息格式 (JSON):
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
        Long userId = (Long) session.getAttributes().get("userId");
        if (roomId == null) {
            closeSession(session);
            return;
        }
        LiveRoom room = liveService.getById(roomId);
        if (room == null || (!"LIVE".equals(room.getStatus()) && !"host".equals(role))) {
            closeSession(session);
            return;
        }
        if (!"host".equals(role) && !"viewer".equals(role)) {
            closeSession(session);
            return;
        }
        // The query role is only a hint. A viewer cannot impersonate the
        // broadcaster, and a PREVIEW room cannot be used as a control bus.
        if ("host".equals(role) && (userId == null || !userId.equals(room.getHostUserId()))) {
            closeSession(session);
            return;
        }
        sessionRoom.put(session.getId(), roomId);
        sessionRole.put(session.getId(), role != null ? role : "viewer");

        // 记录 host 的 userId，用于断线时自动关播
        if (userId != null) {
            sessionUserId.put(session.getId(), userId);
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

        if (message.getPayloadLength() > 8_192) {
            log.warn("Rejected oversized live control payload: roomId={}, bytes={}", roomId,
                    message.getPayloadLength());
            return;
        }

        String payload = message.getPayload();
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set == null) return;

        String role = sessionRole.get(session.getId());
        try {
            var obj = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String type = obj.has("type") ? obj.get("type").asText() : "";
            // Media is never transported over the control WebSocket. Clients
            // must use SRS WHIP/WHEP; silently dropping legacy frame packets
            // prevents accidental base64/binary media fan-out.
            if ("frame".equals(type) || "media".equals(type)) return;
            if ("host".equals(role) && !"chat".equals(type)) return;
            if ("viewer".equals(role) && !"chat".equals(type) && !"like".equals(type)) return;
            if ("chat".equals(type)) {
                String text = obj.has("text") ? obj.get("text").asText("").trim() : "";
                if (text.isEmpty() || text.length() > 500) return;
            }
            if ("like".equals(type)) {
                int count = obj.has("count") ? obj.get("count").asInt(1) : 1;
                if (count < 1 || count > 5) return;
            }
        } catch (Exception ignored) {
            return;
        }

        // Chat/like are broadcast as control events only. The sender receives
        // the same event so all clients share one display projection; media
        // bytes never enter this loop.
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(payload));
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Notify control clients that the provider room ended, then release sockets. */
    public static void broadcastEnd(Long roomId) {
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set == null) return;
        for (WebSocketSession session : set.toArray(new WebSocketSession[0])) {
            if (!session.isOpen()) continue;
            try {
                session.sendMessage(new TextMessage("{\"type\":\"end\"}"));
                session.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // Deliberately no binary media path. Close only malformed/oversized
        // control traffic; normal clients should never send binary here.
        log.warn("Rejected binary live control payload: roomId={}, bytes={}",
                sessionRoom.get(session.getId()), message.getPayloadLength());
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
                    LiveRoom current = liveService.getById(roomId);
                    if (!hostOnline && current != null && "LIVE".equals(current.getStatus())) {
                        liveService.endLive(roomId, userId);
                        broadcastEnd(roomId);
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
