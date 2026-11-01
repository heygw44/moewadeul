package com.moewadeul.trust.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TrustErrorCode implements ErrorCode {
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인확인을 마쳤어요. 따로 할 일은 없어요."),
    IDENTITY_CI_DUPLICATED(HttpStatus.CONFLICT, "다른 계정에서 이미 본인확인을 했어요. 그 계정으로 로그인해 주세요."),
    IDENTITY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 본인확인을 할 수 없어요. 잠시 뒤 다시 시도해 주세요."),
    TRUST_LEVEL_INSUFFICIENT(HttpStatus.FORBIDDEN, "신뢰 단계가 부족해요. 내 정보의 신뢰 단계에서 필요한 조건을 볼 수 있어요."),
    COMPANION_REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "이 베이스캠프의 동행 후기를 쓸 수 없어요. 함께 다녀온 멤버만 쓸 수 있어요."),
    COMPANION_REVIEW_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "동행 후기를 쓸 수 있는 기한이 지났어요. 다음 베이스캠프에서 남겨 주세요."),
    COMPANION_REVIEW_DUPLICATED(HttpStatus.CONFLICT, "이 멤버에게는 이미 동행 후기를 썼어요. 같은 멤버에게는 한 번만 쓸 수 있어요."),
    REPORT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "이 신고는 할 수 없어요. 같은 베이스캠프에 신청했거나 함께한 회원만 신고할 수 있어요."),
    REPORT_DUPLICATED(HttpStatus.CONFLICT, "이미 신고했어요. 처리 결과를 기다려 주세요."),
    REPORT_INVALID_STATE(HttpStatus.CONFLICT, "이미 처리된 신고예요. 화면을 새로고침해 주세요."),
    SANCTION_INVALID_STATE(HttpStatus.CONFLICT, "이미 해제됐거나 끝난 제재예요. 화면을 새로고침해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    TrustErrorCode(HttpStatus httpStatus, String message) {
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
