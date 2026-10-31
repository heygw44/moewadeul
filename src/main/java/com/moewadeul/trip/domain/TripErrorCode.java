package com.moewadeul.trip.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {
    SOLO_TRIP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 안에서, 일정은 1~3박으로 골라 주세요."),
    SOLO_TRIP_LIMIT(HttpStatus.CONFLICT, "출발 전인 혼자 가는 일정은 3개까지 등록할 수 있어요. 내 정보에서 필요 없는 일정을 삭제해 주세요."),
    SOLO_TRIP_OVERLAP(HttpStatus.CONFLICT, "등록한 다른 일정과 날짜가 겹쳐요. 다른 날짜를 골라 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    TripErrorCode(HttpStatus httpStatus, String message) {
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
