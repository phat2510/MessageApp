package com.app.chat_consumer.repository;

import com.app.chat_consumer.entity.ConversationMember;
import com.app.chat_consumer.entity.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId > {
    // Lấy tất cả members của 1 conversation → dùng trong ChatService để fanout
    List<ConversationMember> findById_ConversationId(UUID conversationId);

    // Kiểm tra user có trong conversation không → dùng khi GET history (check quyền)
    boolean existsById_ConversationIdAndId_UserId(UUID conversationId, UUID userId);
}
