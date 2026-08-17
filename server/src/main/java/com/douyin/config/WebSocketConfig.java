package com.douyin.config;

import com.douyin.websocket.ChatWebSocketHandler;
import com.douyin.websocket.DashboardWebSocketHandler;
import com.douyin.websocket.LiveStreamHandler;
import com.douyin.websocket.WebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final LiveStreamHandler liveStreamHandler;
    private final DashboardWebSocketHandler dashboardHandler;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatWebSocketHandler chatHandler,
                           LiveStreamHandler liveStreamHandler,
                           DashboardWebSocketHandler dashboardHandler,
                           WebSocketHandshakeInterceptor handshakeInterceptor,
                           @Value("${websocket.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String allowedOrigins) {
        this.chatHandler = chatHandler;
        this.liveStreamHandler = liveStreamHandler;
        this.dashboardHandler = dashboardHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(this.allowedOrigins);
        registry.addHandler(liveStreamHandler, "/ws/live/**")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(this.allowedOrigins);
        registry.addHandler(dashboardHandler, "/ws/dashboard/stream")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(this.allowedOrigins);
    }
}
