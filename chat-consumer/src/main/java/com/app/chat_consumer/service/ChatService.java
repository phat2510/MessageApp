package com.app.chat_consumer.service;

import com.app.common_shared.dto.ChatMessageEvent; // Kiểm tra kỹ package này!
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private static final String DELIVERY_TOPIC = "delivery-topic";

    @KafkaListener(topics = "chat-topic", groupId = "chat-consumer-group")
    public void consume(ChatMessageEvent event) {
        log.info("[Consumer] Nhận message — senderId={}, clientMessageId={}",
                event.getSenderId(), event.getClientMessageId());

        // TODO: lưu DB ở đây

        // Publish sang delivery-topic để gateway push xuống receiver
        kafkaTemplate.send(DELIVERY_TOPIC, event.getReceiverId(), event);
        log.info("[Consumer] Published delivery-topic — receiverId={}",
                event.getReceiverId());
    }
}