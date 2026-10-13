package com.moewadeul.memory.domain;

/**
 * 추억 기록이 신고와 관리자 조치로 어떤 상태인지. 공개 범위({@link MemoryVisibility})와 따로 바뀐다.
 *
 * <p>작성자가 기록을 지우면 행을 삭제하므로 삭제 상태는 없다. 공개 피드와 회원 추억지도는 공개이면서 ACTIVE인 기록만 보여 준다.
 */
public enum MemoryStatus {
    /** 정상 상태다. */
    ACTIVE,
    /** 검토 전 신고가 쌓여 관리자 검토를 기다린다. 작성자가 아닌 사람에게는 보이지 않는다. */
    PENDING_REVIEW,
    /** 관리자가 숨겼다. 작성자가 아닌 사람에게는 보이지 않는다. */
    HIDDEN
}
