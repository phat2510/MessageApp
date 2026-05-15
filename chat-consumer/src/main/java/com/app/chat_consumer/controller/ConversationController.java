package com.app.chat_consumer.controller;


import com.app.chat_consumer.dto.MessageResponse;
import com.app.chat_consumer.entity.Message;
import com.app.chat_consumer.repository.ConversationMemberRepository;
import com.app.chat_consumer.repository.ConversationRepository;
import com.app.chat_consumer.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {
    private final ConversationRepository coversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;



    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable UUID  conversationId,
                                         @RequestParam String userId,
                                         @RequestParam(defaultValue = "50") int limit) {
        UUID userUUID;
        try {
            userUUID = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("userId không hợp lệ");
        }

        // Kiểm tra user có trong conversation không
        if (!conversationMemberRepository.existsById_ConversationIdAndId_UserId(conversationId, userUUID))
            return ResponseEntity.status(403).body("User không thuộc conversation này");

        List<MessageResponse> result = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId)
                .stream()
                .limit(limit)
                .sorted(Comparator.comparing(Message::getCreatedAt)) // đảo lại cũ → mới
                .map(m -> MessageResponse.builder()
                        .messageId(m.getMessageId().toString())
                        .conversationId(m.getConversationId().toString())
                        .senderId(m.getSenderId().toString())
                        .content(m.getContent())
                        .type(m.getType())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}



