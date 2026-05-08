package com.app.common_shared.dto;

import lombok.Getter;

@Getter
public enum ErrorCodeMessageRoute {
    // Nhóm 1: Lỗi Validation (Đầu vào)
    INVALID_INPUT("VALID_000", "Dữ liệu không hợp lệ"),
    CONTENT_EMPTY("VALID_001", "Nội dung tin nhắn không được để trống"),
    RECEIVER_REQUIRED("VALID_002", "Phải có ID người nhận"),
    SENDER_NAME_REQUIRED("VALID_003", "Thiếu tên người gửi (để tạo template)"),
    CLIENT_MSG_ID_REQUIRED("VALID_004", "Thiếu Client Message ID (chống gửi lặp)"),
    INVALID_MSG_TYPE("VALID_005", "Loại tin nhắn không hỗ trợ (Chỉ nhận TEXT, IMAGE, VIDEO)"),

    // Nhóm 2: Lỗi Logic nghiệp vụ (Business)
    SENDER_RECEIVER_SAME("BIZ_001", "Không thể tự gửi tin nhắn cho chính mình"),
    USER_BLOCKED("BIZ_002", "Bạn đã bị người này chặn"),

    // Nhóm 3: Lỗi Hệ thống/Hạ tầng
    KAFKA_SEND_ERROR("SYS_001", "Lỗi đường truyền Kafka"),
    INTERNAL_SERVER_ERROR("SYS_999", "Lỗi hệ thống không xác định");

    private final String code;
    private final String message;

    ErrorCodeMessageRoute(String code, String message) {
        this.code = code;
        this.message = message;
    }
    // Getter code và message...
}
