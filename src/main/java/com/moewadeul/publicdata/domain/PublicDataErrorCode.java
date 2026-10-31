package com.moewadeul.publicdata.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PublicDataErrorCode implements ErrorCode {
    SYNC_JOB_ALREADY_RUNNING(HttpStatus.CONFLICT, "같은 종류의 작업이 이미 실행 중이에요. 끝난 뒤 다시 실행해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    PublicDataErrorCode(HttpStatus httpStatus, String message) {
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
