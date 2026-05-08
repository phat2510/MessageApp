package com.app.chat_api.exception;

import com.app.common_shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 1. Bắt lỗi Validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "errorCode", err.getDefaultMessage(), // Trả về VALID_001, VALID_002...
                        "message", "Dữ liệu không hợp lệ"
                )).collect(Collectors.toList());

        return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILED",
                "errors", details
        ));
    }

    // 2. Bắt lỗi logic nghiệp vụ tự định nghĩa (Ví dụ: tự gửi cho mình)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", ex.getErrorCode().getCode(),
                "message", ex.getErrorCode().getMessage()
        ));
    }
}
