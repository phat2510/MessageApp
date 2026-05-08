package com.app.chat_api.controller;

import com.app.chat_api.dto.ChatRequestBody;
import com.app.common_shared.dto.ChatMessageEvent; // Mượn từ module common-shared
import com.app.common_shared.dto.ErrorCodeMessageRoute;
import com.app.common_shared.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private static final String TOPIC = "chat-topic";

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody ChatRequestBody request,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestHeader(value = "X-Internal-Request", required = false) String internalHeader) {

        if (!"true".equals(internalHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Truy cập không được phép"));
        }
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Thiếu thông tin người dùng"));
        }

        if (request.getReceiverId().equals(userId)) {
            // Ném lỗi BusinessException đã tạo ở bước trước
            throw new BusinessException(ErrorCodeMessageRoute.SENDER_RECEIVER_SAME);
        }
        log.info("Received message from mobile: {}", request.getClientMessageId());

        // --- LỚP BẢO VỆ 1: Logic Nghiệp vụ (Business Check) ---
        // Giả sử lấy currentUserId từ JWT (tạm thời hardcode là "123")

        // --- XỬ LÝ CHÍNH ---
        // 1. Tạo nội dung theo template
        String formattedContent = String.format("%s: %s",
                request.getSenderName(),
                request.getContent());

        // 2. Chuyển đổi sang Event
        ChatMessageEvent event = ChatMessageEvent.builder()
                .clientMessageId(request.getClientMessageId())
                .senderId(userId)
                .senderName(request.getSenderName())
                .receiverId(request.getReceiverId())
                .content(formattedContent)
                .type(request.getType())
                .timestamp(System.currentTimeMillis())
                .build();

        // --- LỚP BẢO VỆ 2: Hạ tầng (Infrastructure Check) ---
        try {
            // 3. Đẩy vào Kafka
            kafkaTemplate.send(TOPIC, event.getReceiverId(), event);
        } catch (Exception e) {
            log.error("Lỗi gửi tin nhắn lên Kafka: ", e);
            // Nếu Kafka sập, ném lỗi để GlobalExceptionHandler bắt và trả về mã SYS_001
            throw new BusinessException(ErrorCodeMessageRoute.KAFKA_SEND_ERROR);
        }

        // 4. Trả về thành công
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "SUCCESS",
                "clientMessageId", request.getClientMessageId(),
                "message", "Tin nhắn đang được xử lý"
        ));
    }
}