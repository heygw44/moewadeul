package com.moewadeul.spot.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements ErrorCode {
    SPOT_RADIUS_TOO_LARGE(HttpStatus.BAD_REQUEST, "반경은 50km까지 고를 수 있어요. 반경을 줄여 주세요."),
    BAKJI_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확인한 박지예요. 박지마다 한 번만 할 수 있어요."),
    BAKJI_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 이 박지를 신고했어요. 처리 결과를 기다려 주세요."),
    SPOT_INVALID_STATE(HttpStatus.CONFLICT, "상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요."),
    BAKJI_CONCURRENTLY_MODIFIED(HttpStatus.CONFLICT, "상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요.");

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
