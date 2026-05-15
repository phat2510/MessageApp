package com.app.chat_consumer.service;

import com.app.chat_consumer.entity.Message;
import com.app.chat_consumer.repository.ConversationMemberRepository;
import com.app.chat_consumer.repository.MessageRepository;
import com.app.common_shared.dto.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final MessageRepository messageRepository;
    private final ConversationMemberRepository ConversationMemberRepository; // thêm dòng này
    private static final String DELIVERY_TOPIC = "delivery-topic";

    @KafkaListener(topics = "chat-topic", groupId = "chat-consumer-group")
    public void consume(ChatMessageEvent event) {
        log.info("[Consumer] Nhận message — senderId={}, clientMessageId={}",
                event.getSenderId(), event.getClientMessageId());

        Message message = null ;
        List<String> memberIds = null;

        // Lưu DB
        try {
             message = Message.builder()
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
            return;
        }

        try {
            memberIds = ConversationMemberRepository
                    .findById_ConversationId(UUID.fromString(event.getConversationId()))
                    .stream()
                    .map(m -> m.getId().getUserId().toString())
                    .collect(Collectors.toList());
            log.info("[Consumer] {} members", memberIds.size());
        } catch (Exception e) {
            log.error("[Consumer] Lỗi query members: {}", e.getMessage());
            return;
        }
        // 3. fanout : publish delivery event cho từng member
        for (String memberId : memberIds) {
            if (memberId.equals(event.getSenderId())) continue; // bỏ qua sender

            ChatMessageEvent enriched = ChatMessageEvent.builder()
                .clientMessageId(event.getClientMessageId())
                .messageId(message.getMessageId().toString())
                .senderId(event.getSenderId())
                .senderName(event.getSenderName())
                .conversationId(event.getConversationId())
                .memberIds(memberIds)
                    .targetMemberId(memberId) // set đúng member cho event này
                    .content(event.getContent())
                .type(event.getType())
                .timestamp(event.getTimestamp())
                .build();

        // Publish sang delivery-topic
            kafkaTemplate.send(DELIVERY_TOPIC, memberId, enriched);
            log.info("[Consumer] Published delivery-topic — memberId={}", memberId);
        }

    }
}