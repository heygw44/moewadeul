package com.moewadeul.aisearch.application;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AiSearchErrorCode implements ErrorCode {
    AI_SEARCH_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 검색 횟수를 다 썼어요. 내일 다시 써 주세요."),
    AI_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 AI 검색을 쓸 수 없어요. 지역 이름으로 검색해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    AiSearchErrorCode(HttpStatus httpStatus, String message) {
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
