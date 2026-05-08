package com.app.chat_consumer.service;

import com.app.common_shared.dto.ChatMessageEvent; // Kiểm tra kỹ package này!
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatService {

    @KafkaListener(topics = "chat-topic")  // Không hardcode groupId ở đây
    public void consume(ChatMessageEvent event) { // Đổi 'String' thành 'event'
        log.info("------------------------------------------------");
        log.info("Nhận được tin nhắn mới từ Kafka!");
        log.info("Người gửi: {}", event.getSenderId()); // Dùng 'event' thay vì 'String'
        log.info("{}", event.getContent());
        log.info("ID tin nhắn: {}", event.getClientMessageId());
        log.info("------------------------------------------------");
    }
}