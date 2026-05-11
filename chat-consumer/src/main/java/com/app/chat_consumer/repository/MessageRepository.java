package com.app.chat_consumer.repository;

import com.app.chat_consumer.entity.Message;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Field.UUID> {

    // Query lịch sử tin nhắn theo conversation, sort theo created_at DESC
    List<Message> findByConversationIdOrderByCreatedAtDesc(Field.UUID conversationId);
}
