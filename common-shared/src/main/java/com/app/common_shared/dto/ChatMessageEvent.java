package com.app.common_shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEvent implements Serializable {
    private String clientMessageId;
    private String senderName;
    private String senderId;
    private String receiverId;
    private String content;
    private long timestamp;
    private String type; // TEXT, IMAGE, v.v.
}