package com.app.chat_consumer.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private String messageId;
    private String conversationId;
    private String senderId;
    private String content;
    private String type;
    private LocalDateTime createdAt;
}
