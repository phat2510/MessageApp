package com.app.chat_consumer.repository;

import com.app.chat_consumer.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Query lịch sử tin nhắn theo conversation, sort theo created_at DESC
    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}