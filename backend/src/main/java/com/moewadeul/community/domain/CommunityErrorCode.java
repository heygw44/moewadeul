package com.moewadeul.community.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CommunityErrorCode implements ErrorCode {
    COMMUNITY_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 글이나 댓글이에요. 처리 결과를 기다려 주세요."),
    COMMUNITY_INVALID_STATE(HttpStatus.CONFLICT, "상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요.");

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
