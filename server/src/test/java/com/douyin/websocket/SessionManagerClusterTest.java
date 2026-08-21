package com.douyin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagerClusterTest {

    @Test
    void pushDeliversLocallyAndPublishesToOtherNodes() throws Exception {
        SessionManager manager = new SessionManager();
        WebSocketClusterBus bus = mock(WebSocketClusterBus.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("device-a");
        when(socket.isOpen()).thenReturn(true);
        manager.setClusterBus(bus);
        manager.register(7L, socket);

        manager.push(7L, "{\"type\":\"rtc.call.state\"}");

        verify(socket).sendMessage(any());
        verify(bus).publish(7L, "{\"type\":\"rtc.call.state\"}");
    }

    @Test
    void localDeliveryDoesNotRepublish() throws Exception {
        SessionManager manager = new SessionManager();
        WebSocketClusterBus bus = mock(WebSocketClusterBus.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("device-b");
        when(socket.isOpen()).thenReturn(true);
        manager.setClusterBus(bus);
        manager.register(8L, socket);

        manager.pushLocal(8L, "payload");

        verify(socket).sendMessage(any());
        verify(bus, never()).publish(any(), any());
    }

    @Test
    void remoteFanoutDeliversLocallyAndOriginMessageIsIgnored() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SessionManager> provider = mock(ObjectProvider.class);
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        SessionManager manager = mock(SessionManager.class);
        when(provider.getIfAvailable()).thenReturn(manager);
        WebSocketClusterBus bus = new WebSocketClusterBus(
                "node-a", "fanout", redis, new ObjectMapper(), provider, container);

        bus.onMessage(message("{\"origin_node\":\"node-b\",\"user_id\":9,\"payload\":\"hello\"}"), null);
        bus.onMessage(message("{\"origin_node\":\"node-a\",\"user_id\":9,\"payload\":\"loop\"}"), null);

        verify(container).addMessageListener(eq(bus), any(Topic.class));
        verify(manager).pushLocal(9L, "hello");
        verify(manager, never()).pushLocal(9L, "loop");
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage("fanout".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
