package com.moewadeul.program.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProgramErrorCode implements ErrorCode {
    PROGRAM_NOT_IN_APPLY_PERIOD(HttpStatus.BAD_REQUEST, "지금은 신청 기간이 아니에요. 행사 상세에서 신청 기간을 볼 수 있어요."),
    PROGRAM_SOLD_OUT(HttpStatus.CONFLICT, "남은 자리가 없어요. 빈자리 알림을 신청하면 자리가 났을 때 알림을 보내요."),
    PROGRAM_SEATS_AVAILABLE(HttpStatus.CONFLICT, "남은 자리가 있어요. 빈자리 알림 대신 바로 신청해 주세요."),
    PROGRAM_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청한 행사예요. 내 행사 신청에서 볼 수 있어요."),
    PROGRAM_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 기한이 지났어요. 남은 자리가 있으면 다시 신청해 주세요."),
    PROGRAM_INVALID_STATE(HttpStatus.CONFLICT, "상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요."),
    PROGRAM_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 있는 기한이 지났어요. 관리자에게 문의해 주세요."),
    PROGRAM_CAPACITY_DECREASE(HttpStatus.CONFLICT, "신청이 시작된 뒤에는 정원을 줄일 수 없어요. 정원을 늘리거나 그대로 둬 주세요.");

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
