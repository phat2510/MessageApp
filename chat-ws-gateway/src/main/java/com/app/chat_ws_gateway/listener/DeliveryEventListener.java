package com.app.chat_ws_gateway.listener;

import com.app.chat_ws_gateway.handler.ChatWebSocketHandler;
import com.app.common_shared.dto.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventListener {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "delivery-topic", groupId = "gateway-delivery-group")
    public void onDelivery(ChatMessageEvent event) {
        log.info("[Delivery] Nhận event — receiverId={}, clientMessageId={}",
                event.getReceiverId(), event.getClientMessageId());
        try {
            String payload = objectMapper.writeValueAsString(event);
            boolean delivered = chatWebSocketHandler.pushToUser(event.getReceiverId(), payload);
            if (!delivered) {
                log.info("[Delivery] userId={} offline, message đã lưu DB",
                        event.getReceiverId());
            }
        } catch (Exception e) {
            log.error("[Delivery] Lỗi push — receiverId={}: {}",
                    event.getReceiverId(), e.getMessage());
        }
    }
}
