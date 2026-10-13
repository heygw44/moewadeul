package com.moewadeul.trip.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {
    SOLO_TRIP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 안이고 일정은 1~3박이어야 해요."),
    SOLO_TRIP_LIMIT(HttpStatus.CONFLICT, "진행 예정인 개인 일정은 3개까지 등록할 수 있어요."),
    SOLO_TRIP_OVERLAP(HttpStatus.CONFLICT, "등록한 다른 개인 일정과 날짜가 겹쳐요.");

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
