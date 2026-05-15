package com.app.common_shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEvent implements Serializable {
    private String clientMessageId;
    private String targetMemberId;
    private String messageId;
    private String senderId;
    private String senderName;
    private String conversationId;
    private List<String> memberIds;
    private String content;
    private long timestamp;
    private String type;
}