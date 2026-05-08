package com.app.chat_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChatRequestBody {

        @NotBlank(message = "VALID_003")
        private String senderName;

        @NotBlank(message = "VALID_002")
        private String receiverId;

        @NotBlank(message = "VALID_001")
        private String content;

        @NotBlank(message = "VALID_004")
        private String clientMessageId;

        @Pattern(regexp = "^(TEXT|IMAGE|VIDEO)$", message = "VALID_005")
        private String type = "TEXT";
}