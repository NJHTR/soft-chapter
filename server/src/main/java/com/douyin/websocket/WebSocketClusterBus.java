package com.douyin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Low-latency cross-instance WebSocket fanout. MySQL reconciliation remains authoritative;
 * Redis Pub/Sub loss never changes call state and is recovered on reconnect.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.cluster.enabled", havingValue = "true")
public class WebSocketClusterBus implements MessageListener {

    private final String nodeId;
    private final String channel;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SessionManager> sessions;

    public WebSocketClusterBus(@Value("${websocket.cluster.node-id:${HOSTNAME:local}}") String nodeId,
                               @Value("${websocket.cluster.channel:douyin:websocket:fanout:v1}") String channel,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               ObjectProvider<SessionManager> sessions,
                               RedisMessageListenerContainer listenerContainer) {
        this.nodeId = nodeId;
        this.channel = channel;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        listenerContainer.addMessageListener(this, new ChannelTopic(channel));
    }

    public void publish(Long userId, String json) {
        if (userId == null || json == null) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("origin_node", nodeId);
            envelope.put("user_id", userId);
            envelope.put("payload", json);
            redis.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            log.warn("[WS-CLUSTER] publish failed; durable reconciliation remains available: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[WS-CLUSTER] envelope serialization failed: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = objectMapper.readValue(message.getBody(), Map.class);
            if (nodeId.equals(String.valueOf(envelope.get("origin_node")))) {
                return;
            }
            Object rawUserId = envelope.get("user_id");
            Object payload = envelope.get("payload");
            if (rawUserId == null || payload == null) {
                return;
            }
            long userId = rawUserId instanceof Number number
                    ? number.longValue() : Long.parseLong(String.valueOf(rawUserId));
            SessionManager manager = sessions.getIfAvailable();
            if (manager != null) {
                manager.pushLocal(userId, String.valueOf(payload));
            }
        } catch (Exception e) {
            log.warn("[WS-CLUSTER] invalid fanout message ignored: {}", e.getMessage());
        }
    }
}
