package com.app.chat_ws_gateway.handler;

import com.app.chat_ws_gateway.dto.WsMessagePayload;
import com.app.chat_ws_gateway.filter.WsPayloadFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WsPayloadFilter payloadFilter;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${internal.chat-api.url}")
    private String chatApiUrl;

    // Lưu các session đang active: userId -> session
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // ---- Lifecycle ----

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        activeSessions.put(userId, session);
        log.info("[WS] Connected — userId={}", userId);
        sendAck(session, "CONNECTED", null, "Kết nối thành công");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        activeSessions.remove(userId);
        log.info("[WS] Disconnected — userId={}", userId);
    }

    // ---- Xử lý message ----

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = (String) session.getAttributes().get("userId");

        // Filter 2: validate payload
        WsMessagePayload payload;
        try {
            payload = payloadFilter.validateAndParse(message.getPayload(), userId);
        } catch (WsPayloadFilter.PayloadFilterException e) {
            log.warn("[Filter-Payload] userId={} — {}", userId, e.getMessage());
            sendAck(session, "ERROR", null, e.getMessage());
            return;
        }

        // Gọi chat-api nội bộ
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId);
            headers.set("X-Internal-Request", "true");
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of(
                    "clientMessageId", payload.getClientMessageId(),
                    "receiverId", payload.getReceiverId(),
                    "content", payload.getContent(),
                    "senderName", userId,
                    "type", payload.getType()
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    chatApiUrl + "/api/v1/chat/send", entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WS] Forwarded — userId={}, clientMessageId={}",
                        userId, payload.getClientMessageId());
                sendAck(session, "SENT", payload.getClientMessageId(), "Tin nhắn đang được xử lý");
            } else {
                sendAck(session, "ERROR", payload.getClientMessageId(), "Lỗi khi gửi tin nhắn");
            }

        } catch (Exception e) {
            log.error("[WS] Lỗi gọi chat-api — userId={}: {}", userId, e.getMessage());
            sendAck(session, "ERROR", payload.getClientMessageId(), "Lỗi hệ thống");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = (String) session.getAttributes().get("userId");
        log.error("[WS] Transport error — userId={}: {}", userId, exception.getMessage());
    }

    // ---- Helper ----

    private void sendAck(WebSocketSession session, String status,
                         String clientMessageId, String message) {
        try {
            Map<String, String> ack = clientMessageId != null
                    ? Map.of("status", status, "clientMessageId", clientMessageId, "message", message)
                    : Map.of("status", status, "message", message);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (Exception e) {
            log.error("Không thể gửi ACK: {}", e.getMessage());
        }
    }
}
