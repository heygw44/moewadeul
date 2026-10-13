package com.moewadeul.program.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProgramErrorCode implements ErrorCode {
    PROGRAM_NOT_IN_APPLY_PERIOD(HttpStatus.BAD_REQUEST, "신청 기간이 아니에요."),
    PROGRAM_SOLD_OUT(HttpStatus.CONFLICT, "남은 자리가 없어요. 빈자리 알림을 신청하면 자리가 났을 때 알림을 보내요."),
    PROGRAM_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청한 행사예요."),
    PROGRAM_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 기한이 지났어요."),
    PROGRAM_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 동작이에요."),
    PROGRAM_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 있는 기한이 지났어요."),
    PROGRAM_CAPACITY_DECREASE(HttpStatus.CONFLICT, "신청이 시작된 행사는 정원을 줄일 수 없어요.");

    private final HttpStatus httpStatus;
    private final String message;

    ProgramErrorCode(HttpStatus httpStatus, String message) {
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
