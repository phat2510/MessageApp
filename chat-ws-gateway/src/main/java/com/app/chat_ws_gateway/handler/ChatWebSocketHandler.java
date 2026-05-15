package com.app.chat_ws_gateway.handler;

import com.app.chat_ws_gateway.dto.WsMessagePayload;
import com.app.chat_ws_gateway.filter.WsPayloadFilter;
import com.app.common_shared.dto.ChatMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WsPayloadFilter payloadFilter;
    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String CHAT_TOPIC = "chat-topic";
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

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

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = (String) session.getAttributes().get("userId");

        WsMessagePayload payload;
        try {
            payload = payloadFilter.validateAndParse(message.getPayload(), userId);
        } catch (WsPayloadFilter.PayloadFilterException e) {
            log.warn("[Filter-Payload] userId={} — {}", userId, e.getMessage());
            sendAck(session, "ERROR", null, e.getMessage());
            return;
        }

        try {
            ChatMessageEvent event = ChatMessageEvent.builder()
                    .clientMessageId(payload.getClientMessageId())
                    .senderId(userId)
                    .senderName(userId)
                    .conversationId(payload.getConversationId())
                    .content(payload.getContent())
                    .type(payload.getType())
                    .timestamp(System.currentTimeMillis())
                    .build();

            kafkaTemplate.send(CHAT_TOPIC, event.getConversationId(), event);
            log.info("[WS] Published Kafka — userId={}, clientMessageId={}",
                    userId, payload.getClientMessageId());
            sendAck(session, "SENT", payload.getClientMessageId(), "Tin nhắn đang được xử lý");

        } catch (Exception e) {
            log.error("[WS] Lỗi publish Kafka — userId={}: {}", userId, e.getMessage());
            sendAck(session, "ERROR", payload.getClientMessageId(), "Lỗi hệ thống");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = (String) session.getAttributes().get("userId");
        log.error("[WS] Transport error — userId={}: {}", userId, exception.getMessage());
    }

    public boolean pushToUser(String userId, String jsonPayload) {
        WebSocketSession session = activeSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonPayload));
                log.info("[WS] Pushed — userId={}", userId);
                return true;
            } catch (Exception e) {
                log.error("[WS] Lỗi push userId={}: {}", userId, e.getMessage());
            }
        }
        log.info("[WS] userId={} offline", userId);
        return false;
    }

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