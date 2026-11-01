package com.moewadeul.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 내용에 맞지 않는 곳이 있어요. 내용을 확인한 뒤 다시 시도해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인한 뒤 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 기능을 쓸 권한이 없어요. 이전 화면으로 돌아가 주세요."),
    MEMBER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증을 마친 뒤에 할 수 있어요. 메일로 받은 인증 코드를 입력해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 내용이 없거나 삭제됐어요. 목록에서 다시 골라 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "요청을 처리하지 못했어요. 화면을 새로고침한 뒤 다시 시도해 주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "요청을 처리하지 못했어요. 화면을 새로고침한 뒤 다시 시도해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요."),
    IMAGE_UPLOAD_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "올린 뒤 아직 쓰지 않은 사진이 너무 많아요. 잠시 뒤 다시 올려 주세요."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 사진을 확인할 수 없어요. 잠시 뒤 다시 시도해 주세요."),
    RESOURCE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "다른 요청과 겹쳐 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "요청을 처리하지 못했어요. 화면을 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "요청을 처리하지 못했어요. 화면을 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "앞서 보낸 요청을 아직 처리하고 있어요. 잠시 기다려 주세요.");

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
