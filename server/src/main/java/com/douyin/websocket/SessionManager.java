package com.douyin.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有在线用户的 WebSocket 连接。
 * ChatWebSocketHandler 注册/注销连接，Kafka 消费者通过此类推送消息。
 */
@Slf4j
@Component
public class SessionManager {

    /** userId -> WebSocketSession */
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("Session registered: userId={}, total={}", userId, sessions.size());
    }

    public void unregister(Long userId) {
        sessions.remove(userId);
        log.info("Session unregistered: userId={}, total={}", userId, sessions.size());
    }

    public WebSocketSession get(Long userId) {
        return sessions.get(userId);
    }

    public boolean isOnline(Long userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    /** 推送 JSON 消息给指定用户，不在线则静默跳过 */
    public void push(Long userId, String json) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Push failed: userId={}", userId, e);
            }
        }
    }

    /** 推送 JSON 给指定用户，同时回显给发送者 */
    public void pushBoth(Long fromUserId, Long toUserId, String json) {
        // 发给接收者
        push(toUserId, json);
        // 回显给发送者
        push(fromUserId, json);
    }
}
