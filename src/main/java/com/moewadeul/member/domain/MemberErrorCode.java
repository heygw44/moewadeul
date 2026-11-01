package com.moewadeul.member.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요. 로그인하거나 다른 이메일을 써 주세요."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "다른 회원이 쓰는 닉네임이에요. 다른 닉네임을 정해 주세요."),
    MEMBER_DISPOSABLE_EMAIL(HttpStatus.BAD_REQUEST, "일회용 이메일은 쓸 수 없어요. 평소 쓰는 이메일을 입력해 주세요."),
    MEMBER_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "비밀번호는 10~64자로 정하고 영문, 숫자, 특수문자를 각각 1자 이상 넣어 주세요."),
    MEMBER_CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않아요. 다시 입력해 주세요."),
    MEMBER_PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "지금 쓰는 비밀번호와 같아요. 다른 비밀번호를 정해 주세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일이나 비밀번호가 맞지 않아요. 다시 입력해 주세요."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인에 여러 번 실패해서 잠겼어요. 조금 뒤 다시 시도해 주세요."),
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정이에요. 정지가 풀린 뒤 다시 로그인해 주세요."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않아요. 메일에 적힌 코드를 다시 입력해 주세요."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료됐어요. 코드를 다시 받아 주세요."),
    EMAIL_CODE_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 코드를 5번 틀렸어요. 코드를 다시 받아 주세요."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 이메일 인증을 마쳤어요. 그대로 쓰면 돼요."),
    EMAIL_RESEND_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증 코드를 너무 자주 요청했어요. 잠시 뒤 다시 시도해 주세요."),
    SIGNUP_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "가입 요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "만료됐거나 이미 쓴 재설정 링크예요. 비밀번호 재설정을 다시 요청해 주세요."),
    PASSWORD_RESET_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "비밀번호 재설정을 너무 자주 요청했어요. 잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    MemberErrorCode(HttpStatus httpStatus, String message) {
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
