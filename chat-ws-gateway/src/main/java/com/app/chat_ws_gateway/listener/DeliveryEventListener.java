package com.app.chat_ws_gateway.listener;


import com.app.chat_ws_gateway.handler.ChatWebSocketHandler;
import com.app.common_shared.dto.ChatMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventListener {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "delivery-topic", groupId = "gateway-delivery-group")
    public void onDelivery(ChatMessageEvent event) {
        log.info("[Delivery] Nhận event — conversationId={}, targetMemberId={}",
                event.getConversationId(), event.getTargetMemberId());
        try {
            String payload = objectMapper.writeValueAsString(event);
            boolean delivered = chatWebSocketHandler.pushToUser(event.getTargetMemberId(), payload);
            if (!delivered) {
                log.info("[Delivery] userId={} offline", event.getTargetMemberId());
            }
        } catch (Exception e) {
            log.error("[Delivery] Lỗi push: {}", e.getMessage());
        }
    }

}
