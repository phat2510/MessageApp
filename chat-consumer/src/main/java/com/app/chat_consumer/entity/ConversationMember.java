package com.app.chat_consumer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}