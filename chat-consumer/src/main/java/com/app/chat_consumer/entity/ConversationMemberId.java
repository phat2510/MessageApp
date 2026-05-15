package com.app.chat_consumer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberId implements Serializable {

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "user_id")
    private UUID userId;
}