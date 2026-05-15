package com.app.chat_consumer.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateConversationRequest {
    private String type;
    private List<String> memberIds;
}
