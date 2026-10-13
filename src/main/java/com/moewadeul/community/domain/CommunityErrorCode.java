package com.moewadeul.community.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CommunityErrorCode implements ErrorCode {
    COMMUNITY_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 글이나 댓글이에요."),
    COMMUNITY_INVALID_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 조치예요.");

    private final HttpStatus httpStatus;
    private final String message;

    CommunityErrorCode(HttpStatus httpStatus, String message) {
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
