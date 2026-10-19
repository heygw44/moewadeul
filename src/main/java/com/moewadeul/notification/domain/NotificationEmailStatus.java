package com.moewadeul.notification.domain;

/** 알림 한 건의 메일 상태. 알림은 PENDING이나 SKIPPED로 만들고, 메일 발송 작업이 PENDING을 SENT·SKIPPED·FAILED로 바꾼다. */
public enum NotificationEmailStatus {
    /** 메일을 보낼 후보. 발송 작업이 집을 때 수신 설정과 회원 상태를 다시 보고 보낼지 정한다. */
    PENDING,
    SENT,
    /** 보내지 않기로 한 알림. 이벤트 하나의 메일 상한을 넘었거나, 회원이 그 종류의 이메일을 껐거나, 메일을 받을 수 없는 회원이다. */
    SKIPPED,
    /** 재시도 한도만큼 실패해서 더 보내지 않는 알림. */
    FAILED
}
