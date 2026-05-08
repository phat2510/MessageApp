package com.app.common_shared.exception;

import com.app.common_shared.dto.ErrorCodeMessageRoute;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeMessageRoute errorCode;
    public BusinessException(ErrorCodeMessageRoute errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
