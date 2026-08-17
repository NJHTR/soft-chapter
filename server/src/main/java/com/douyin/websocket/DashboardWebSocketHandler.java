package com.douyin.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.ActiveSession;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.ActiveSessionMapper;
import com.douyin.mapper.LiveRoomMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private final ActiveSessionMapper activeSessionMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> clients = ConcurrentHashMap.newKeySet();

    public DashboardWebSocketHandler(ActiveSessionMapper activeSessionMapper,
                                     LiveRoomMapper liveRoomMapper) {
        this.activeSessionMapper = activeSessionMapper;
        this.liveRoomMapper = liveRoomMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        clients.add(session);
        log.info("Dashboard WS connected: {}, total clients: {}", session.getId(), clients.size());
        pushMetrics(); // Send immediately on connect
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session);
        log.info("Dashboard WS disconnected: {}, total clients: {}", session.getId(), clients.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        clients.remove(session);
    }

    @Scheduled(fixedRate = 5000)
    public void pushMetrics() {
        if (clients.isEmpty()) return;
        try {
            Map<String, Object> payload = buildMetricsPayload();
            String json = objectMapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            List<WebSocketSession> dead = new ArrayList<>();
            for (WebSocketSession s : clients) {
                try {
                    if (s.isOpen()) s.sendMessage(msg);
                    else dead.add(s);
                } catch (IOException e) { dead.add(s); }
            }
            clients.removeAll(dead);
        } catch (Exception e) {
            log.error("Dashboard WS push error", e);
        }
    }

    private Map<String, Object> buildMetricsPayload() {
        long onlineUsers = activeSessionMapper.selectCount(
                new LambdaQueryWrapper<ActiveSession>().eq(ActiveSession::getIsActive, 1));
        long activeLiveRooms = liveRoomMapper.selectCount(
                new LambdaQueryWrapper<LiveRoom>().eq(LiveRoom::getStatus, "LIVE"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", System.currentTimeMillis() / 1000);
        payload.put("onlineUsers", onlineUsers);
        payload.put("activeLiveRooms", activeLiveRooms);
        return payload;
    }
}
