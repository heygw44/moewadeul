package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BasecampErrorCode implements ErrorCode {
    BASECAMP_CAPACITY_INVALID(HttpStatus.BAD_REQUEST, "정원은 캠프 리더를 포함해 2~6명으로 정해 주세요. 연 뒤에는 정원을 줄일 수 없어요."),
    BASECAMP_OPEN_LIMIT(HttpStatus.BAD_REQUEST, "모집 중이거나 마감한 베이스캠프는 3개까지 열 수 있어요. 하나를 확정하거나 취소한 뒤 열어 주세요."),
    BASECAMP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 안에서, 일정은 1~3박으로 골라 주세요."),
    BASECAMP_WARNING_SPOT(HttpStatus.BAD_REQUEST, "공원 경계 경고가 있는 박지에서는 베이스캠프를 열 수 없어요. 다른 장소를 골라 주세요."),
    BASECAMP_CONDITION_NOT_MET(HttpStatus.FORBIDDEN, "이 베이스캠프의 합류 조건에 맞지 않아요. 다른 베이스캠프를 찾아 주세요."),
    BASECAMP_DATE_CONFLICT(HttpStatus.CONFLICT, "같은 기간에 이미 함께하는 다른 베이스캠프가 있어요."),
    BASECAMP_PENDING_LIMIT(HttpStatus.CONFLICT, "이 베이스캠프에 답을 기다리는 신청이 가득 찼어요. 다른 베이스캠프를 찾아 주세요."),
    BASECAMP_REAPPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "이 베이스캠프에는 다시 합류 신청할 수 없어요. 다른 베이스캠프를 찾아 주세요."),
    BASECAMP_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 합류 신청했거나 멤버인 베이스캠프예요. 내 베이스캠프에서 볼 수 있어요."),
    BASECAMP_FULL(HttpStatus.CONFLICT, "정원이 모두 찼어요. 다른 베이스캠프를 찾아 주세요."),
    BASECAMP_INVALID_STATE(HttpStatus.CONFLICT, "상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요."),
    BASECAMP_NOT_ENOUGH_MEMBERS(HttpStatus.CONFLICT, "멤버가 2명 이상이어야 확정할 수 있어요. 합류 신청을 더 받아 주세요."),
    BASECAMP_LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "캠프 리더는 베이스캠프에서 나갈 수 없어요. 나가려면 베이스캠프를 취소해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    BasecampErrorCode(HttpStatus httpStatus, String message) {
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
