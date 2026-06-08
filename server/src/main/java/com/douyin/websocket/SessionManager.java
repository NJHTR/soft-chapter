package com.douyin.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 管理所有在线用户的 WebSocket 连接（支持单用户多设备同时在线）。
 * ChatWebSocketHandler 注册/注销连接，Kafka 消费者通过此类推送消息。
 */
@Slf4j
@Component
public class SessionManager {

    /** userId -> 该用户所有设备的 WebSocketSession 集合 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("Session registered: userId={}, sessionId={}, deviceCount={}",
                userId, session.getId(), sessions.get(userId).size());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId);
            }
        }
        log.info("Session unregistered: userId={}, sessionId={}, remainingDevices={}",
                userId, session.getId(), set == null ? 0 : set.size());
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return false;
        // 清理已关闭的连接
        set.removeIf(s -> !s.isOpen());
        if (set.isEmpty()) {
            sessions.remove(userId);
            return false;
        }
        return true;
    }

    /** 推送 JSON 消息给指定用户的所有在线设备 */
    public void push(Long userId, String json) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return;
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.error("Push failed: userId={}, sessionId={}", userId, session.getId(), e);
                }
            }
        }
    }

    /** 推送 JSON 给指定用户的所有设备，同时回显给发送者的所有设备 */
    public void pushBoth(Long fromUserId, Long toUserId, String json) {
        push(toUserId, json);
        push(fromUserId, json);
    }

    /** 推送给群成员列表中的所有人在线设备（除发送者） */
    public void pushToGroupMembers(Collection<Long> memberUids, Long excludeUid, String json) {
        for (Long uid : memberUids) {
            if (!uid.equals(excludeUid)) {
                push(uid, json);
            }
        }
    }
}
