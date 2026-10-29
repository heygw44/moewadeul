package com.moewadeul.common.error;

import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException notFound() {
        return BusinessException.notFound();
    }

    public Map<String, Object> extraFields() {
        return Map.of();
    }
}
