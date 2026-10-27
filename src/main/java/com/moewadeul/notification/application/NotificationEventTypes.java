package com.moewadeul.notification.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class NotificationEventTypes {

    public static final String BASECAMP_APPLIED = "BASECAMP_APPLIED";
    public static final String BASECAMP_APPROVED = "BASECAMP_APPROVED";
    public static final String BASECAMP_REJECTED = "BASECAMP_REJECTED";
    public static final String BASECAMP_KICKED = "BASECAMP_KICKED";
    public static final String BASECAMP_CONFIRMED = "BASECAMP_CONFIRMED";
    public static final String BASECAMP_CANCELED = "BASECAMP_CANCELED";
    public static final String BASECAMP_COMPLETED = "BASECAMP_COMPLETED";
    public static final String BASECAMP_MEMBER_CHANGED = "BASECAMP_MEMBER_CHANGED";
    public static final String MEMBER_REPORT_RESOLVED = "MEMBER_REPORT_RESOLVED";
    public static final String SANCTION_CONFIRMED = "SANCTION_CONFIRMED";
    public static final String PROGRAM_APPLICATION_CONFIRMED = "PROGRAM_APPLICATION_CONFIRMED";
    public static final String PROGRAM_APPLICATION_CANCELED = "PROGRAM_APPLICATION_CANCELED";
    public static final String PROGRAM_APPLICATION_EXPIRED = "PROGRAM_APPLICATION_EXPIRED";
    public static final String PROGRAM_SEAT_RELEASED = "PROGRAM_SEAT_RELEASED";

    public static final List<String> EMAIL_CONFIGURABLE = List.of(
            BASECAMP_APPLIED,
            BASECAMP_APPROVED,
            BASECAMP_REJECTED,
            BASECAMP_KICKED,
            BASECAMP_CONFIRMED,
            BASECAMP_CANCELED,
            BASECAMP_COMPLETED,
            BASECAMP_MEMBER_CHANGED,
            MEMBER_REPORT_RESOLVED,
            SANCTION_CONFIRMED,
            PROGRAM_APPLICATION_CONFIRMED,
            PROGRAM_APPLICATION_CANCELED,
            PROGRAM_APPLICATION_EXPIRED,
            PROGRAM_SEAT_RELEASED);

    public static final boolean DEFAULT_EMAIL_ENABLED = true;

    public static final Map<String, Integer> EMAIL_LIMIT_PER_EVENT = Map.of(PROGRAM_SEAT_RELEASED, 50);

    /**
     * 운영 메일 계정(Gmail)은 최근 24시간에 약 500통까지만 보낸다. 알림 메일이 이 한도를 다 쓰면 인증 코드와 비밀번호 재설정 메일이
     * 나가지 못한다. 그래서 알림 메일은 300통까지만 보내고, 나머지 200통은 인증 코드와 재설정 메일 몫으로 남긴다.
     * Gmail이 달력 날짜가 아니라 최근 24시간으로 세므로 상한도 같은 방식으로 센다.
     */
    public static final int EMAIL_LIMIT_PER_WINDOW = 300;

    public static final Duration EMAIL_LIMIT_WINDOW = Duration.ofHours(24);

    private NotificationEventTypes() {}
}
