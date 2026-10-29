package com.moewadeul.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 값이 맞지 않아요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    MEMBER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요해요."),
    TRUST_LEVEL_INSUFFICIENT(HttpStatus.FORBIDDEN, "신뢰 단계가 부족해요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드예요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type이에요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요."),
    IMAGE_UPLOAD_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "올려 두고 아직 붙이지 않은 사진이 너무 많아요. 잠시 뒤 다시 올려 주세요."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 사진을 확인할 수 없어요. 잠시 뒤 다시 시도해 주세요."),
    RESOURCE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "멱등성 키가 필요해요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "같은 멱등성 키로 다른 요청을 보낼 수 없어요."),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "같은 멱등성 키로 보낸 요청을 아직 처리하고 있어요.");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
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
