package com.douyin.websocket;

import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.ChatMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket 实时聊天处理器。
 *
 * 消息流转链路：
 * 客户端A → WS → ChatWebSocketHandler → Kafka(chat-messages)
 *   → KafkaMessageConsumer → DB(t_message) + WS推送 → 客户端B
 *
 * 优点：接入与推送解耦，Kafka 缓冲削峰，消费者可水平扩展。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessagePublisher messagePublisher;
    private final SessionManager sessionManager;

    public ChatWebSocketHandler(MessagePublisher messagePublisher, SessionManager sessionManager) {
        this.messagePublisher = messagePublisher;
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.register(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        Long fromUserId = (Long) session.getAttributes().get("userId");
        if (fromUserId == null) return;

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(textMessage.getPayload(), Map.class);
            payload = p;
        } catch (Exception e) {
            log.error("Invalid WebSocket message: {}", textMessage.getPayload());
            return;
        }

        Long toUserId = payload.get("to_user_id") != null
                ? Long.valueOf(payload.get("to_user_id").toString()) : null;
        String content = (String) payload.getOrDefault("content", "");
        Integer msgType = payload.get("msg_type") != null
                ? Integer.valueOf(payload.get("msg_type").toString()) : 1;
        String extra = (String) payload.getOrDefault("extra", "");

        if (toUserId == null) return;

        // 发布到 Kafka，后续由消费者异步落库 + 推送
        ChatMessageEvent event = new ChatMessageEvent();
        event.setFromUserId(fromUserId);
        event.setToUserId(toUserId);
        event.setContent(content);
        event.setMsgType(msgType);
        event.setExtra(extra);
        event.setTimestamp(System.currentTimeMillis());

        messagePublisher.publishChat(event);
        log.debug("Chat event published to Kafka: from={} to={}", fromUserId, toUserId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: userId={}", session.getAttributes().get("userId"), exception);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId);
        }
    }
}
