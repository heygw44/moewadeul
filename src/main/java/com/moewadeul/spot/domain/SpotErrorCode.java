package com.moewadeul.spot.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements ErrorCode {
    SPOT_RADIUS_TOO_LARGE(HttpStatus.BAD_REQUEST, "반경은 50km 이하여야 해요."),
    BAKJI_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확인한 박지예요."),
    BAKJI_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 박지예요."),
    SPOT_INVALID_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 조치예요.");

    private final HttpStatus httpStatus;
    private final String message;

    SpotErrorCode(HttpStatus httpStatus, String message) {
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
