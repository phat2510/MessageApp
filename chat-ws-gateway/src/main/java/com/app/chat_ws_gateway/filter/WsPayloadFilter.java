package com.app.chat_ws_gateway.filter;

import com.app.chat_ws_gateway.dto.WsMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class WsPayloadFilter {

    private final ObjectMapper objectMapper;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    public WsMessagePayload validateAndParse(String rawPayload, String senderId)
            throws PayloadFilterException {

        // Check kích thước
        if (rawPayload == null || rawPayload.getBytes().length > MAX_PAYLOAD_BYTES) {
            throw new PayloadFilterException("Payload vượt quá giới hạn 64KB");
        }

        // Parse JSON
        WsMessagePayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WsMessagePayload.class);
        } catch (Exception e) {
            throw new PayloadFilterException("Payload không đúng định dạng JSON");
        }

        // Validate field bắt buộc
        if (isBlank(payload.getReceiverId()))
            throw new PayloadFilterException("Thiếu receiverId");
        if (isBlank(payload.getContent()))
            throw new PayloadFilterException("Thiếu content");
        if (isBlank(payload.getClientMessageId()))
            throw new PayloadFilterException("Thiếu clientMessageId");

        // Sender != receiver
        if (senderId.equals(payload.getReceiverId()))
            throw new PayloadFilterException("Không thể gửi tin nhắn cho chính mình");

        // Validate type
        String type = payload.getType() != null ? payload.getType() : "TEXT";
        if (!type.matches("^(TEXT|IMAGE|VIDEO)$"))
            throw new PayloadFilterException("Type không hợp lệ: " + type);

        // Sanitize
        payload.setContent(payload.getContent().replaceAll("<[^>]*>", "").trim());
        payload.setType(type);

        return payload;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static class PayloadFilterException extends Exception {
        public PayloadFilterException(String message) {
            super(message);
        }
    }
}
