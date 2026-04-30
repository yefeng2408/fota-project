package com.yef.fota.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yef.fota.api.dto.DeviceUpgradeEventRequest;
import com.yef.fota.api.dto.resp.UpgradeCancelEventResult;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
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
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final WebSocketHandler deviceUpgradeWebSocketHandler = new DeviceUpgradeWebSocketHandler();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceUpgradeWebSocketHandler, "/ws/device-upgrade")
                .setAllowedOrigins("*");
    }

    public void pushDeviceUpgradeEvent(DeviceUpgradeEventRequest event) {
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
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
                sessions.remove(entry.getKey());
            }
        }
    }


    public void pushDeviceUpgradeCancelEvent(UpgradeCancelEventResult event) {
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
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
                sessions.remove(entry.getKey());
            }
        }
    }




    private class DeviceUpgradeWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                    session,
                    SEND_TIME_LIMIT_MS,
                    BUFFER_SIZE_LIMIT_BYTES
            );
            sessions.put(session.getId(), safeSession);
            System.out.println("WebSocket connected, sessionId=" + session.getId() + ", currentSessions=" + sessions.size());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sessions.remove(session.getId());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            sessions.remove(session.getId());
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        }
    }

}
