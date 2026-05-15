package com.app.chat_consumer.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationResponse {
    private String conversationId;
    private String type;
    private LocalDateTime createdAt;
    private List<String> memberIds;
}