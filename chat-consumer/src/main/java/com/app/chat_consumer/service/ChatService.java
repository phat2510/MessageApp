package com.app.chat_consumer.service;

import com.app.chat_consumer.entity.Message;
import com.app.chat_consumer.repository.MessageRepository;
import com.app.common_shared.dto.ChatMessageEvent; // Kiểm tra kỹ package này!
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final MessageRepository messageRepository;
    private static final String DELIVERY_TOPIC = "delivery-topic";

    @KafkaListener(topics = "chat-topic", groupId = "chat-consumer-group")
    public void consume(ChatMessageEvent event) {
        log.info("[Consumer] Nhận message — senderId={}, clientMessageId={}",
                event.getSenderId(), event.getClientMessageId());

        // Lưu DB
        try {
            Message message = Message.builder()
                    .conversationId(UUID.fromString(event.getConversationId()))
                    .senderId(UUID.fromString(event.getSenderId()))
                    .content(event.getContent())
                    .type(event.getType())
                    .createdAt(LocalDateTime.now())
                    .build();
            messageRepository.save(message);
            log.info("[Consumer] Đã lưu DB — messageId={}", message.getMessageId());
        } catch (Exception e) {
            log.error("[Consumer] Lỗi lưu DB: {}", e.getMessage());
        }

        // Publish sang delivery-topic
        kafkaTemplate.send(DELIVERY_TOPIC, event.getReceiverId(), event);
        log.info("[Consumer] Published delivery-topic — receiverId={}", event.getReceiverId());
    }
}