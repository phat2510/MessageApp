package com.app.chat_ws_gateway.dto;

import lombok.Data;

@Data
public class WsMessagePayload {
    private String clientMessageId;

    private String conversationId; // thêm mới
    private String content;
    private String type;
}