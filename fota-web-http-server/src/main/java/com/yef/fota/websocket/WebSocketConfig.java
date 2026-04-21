package com.yef.fota.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yef.fota.api.dto.DeviceUpgradeEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description: 设备升级进度与状态的 WebSocket 推送配置
 * @author: 叶丰
 * @date: 2026/4/21 10:40
 */
@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final WebSocketHandler deviceUpgradeWebSocketHandler = new DeviceUpgradeWebSocketHandler();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceUpgradeWebSocketHandler, "/ws/device-upgrade")
                .setAllowedOrigins("*");
    }

    public void pushDeviceUpgradeEvent(DeviceUpgradeEvent event) {
        if (event == null) {
            return;
        }
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("设备升级事件序列化失败", e);
        }

        TextMessage message = new TextMessage(payload);
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
                sessions.remove(session);
            }
        }
    }

    private class DeviceUpgradeWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            sessions.add(session);
            System.out.println("WebSocket connected, sessionId=" + session.getId() + ", currentSessions=" + sessions.size());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sessions.remove(session);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            sessions.remove(session);
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        }
    }

}
