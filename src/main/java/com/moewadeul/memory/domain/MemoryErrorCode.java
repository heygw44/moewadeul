package com.moewadeul.memory.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemoryErrorCode implements ErrorCode {
    MEMORY_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 기록이에요."),
    MEMORY_INVALID_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 조치예요.");

    private final HttpStatus httpStatus;
    private final String message;

    MemoryErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
