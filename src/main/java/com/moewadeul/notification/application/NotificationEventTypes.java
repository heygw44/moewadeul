package com.moewadeul.notification.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 알림을 만드는 이벤트 종류 이름이다. 이벤트를 기록하는 basecamp·trust·program 모듈의 상수와 같은 값이어야 한다.
 * notification 모듈은 그 모듈의 클래스를 가져다 쓰지 않으므로 문자열을 따로 둔다. 기록하는 쪽의 값을 바꾸면 여기도 함께 바꾼다.
 */
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

    /** 회원이 이메일 수신 여부를 정할 수 있는 알림 종류다. 설정 화면에 이 순서대로 보여 준다. */
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

    /** 회원이 설정을 저장하지 않은 알림 종류의 이메일 수신 여부다. 기본은 받는 것이다. */
    public static final boolean DEFAULT_EMAIL_ENABLED = true;

    /**
     * 이벤트 하나로 메일을 보낼 회원 수의 상한이 있는 알림 종류다. 상한이 없는 종류는 이 표에 없다.
     *
     * <p>빈자리 알림은 자리가 한 번 돌아올 때마다 알림을 먼저 신청한 회원부터 50명까지만 메일을 받는다. 나머지는 알림함에만 받는다.
     * 운영 메일 계정의 하루 발송 한도가 500통이라, 신청자가 수천 명인 행사에서 빈자리 메일이 한도를 다 쓰면 그날 인증 코드와 비밀번호 재설정
     * 메일을 보내지 못하기 때문이다. 이메일을 껐거나 메일을 받을 수 없는 회원은 50명에 세지 않는다.
     * 같은 이유로 행사 쪽은 마지막 알림 뒤 30분 안에 자리가 또 돌아오면 그 회원을 이벤트 대상에서 뺀다. 그래서 결제 만료가 한꺼번에
     * 몰려도 빈자리 메일은 행사마다 30분에 50통을 넘지 않는다.
     */
    public static final Map<String, Integer> EMAIL_LIMIT_PER_EVENT = Map.of(PROGRAM_SEAT_RELEASED, 50);

    /**
     * 모든 종류의 알림 메일을 합쳐 {@link #EMAIL_LIMIT_WINDOW} 동안 보낼 수 있는 수의 상한이다.
     *
     * <p>운영 메일 계정(Gmail)은 최근 24시간에 약 500통까지만 보낸다. 알림 메일이 이 한도를 다 쓰면 인증 코드와 비밀번호 재설정 메일이
     * 나가지 못한다. 예를 들어 정원 1,000명 행사의 결제 대기 신청이 한꺼번에 만료되면 만료 알림 메일만 1,000통이 생긴다. 그래서 알림 메일은
     * 300통까지만 보내고, 나머지 200통은 인증 코드와 재설정 메일 몫으로 남긴다. 상한에 닿은 알림은 알림함에는 그대로 있고, 메일은 보낸 지
     * 24시간이 지난 메일이 기간에서 빠지는 만큼 알림 순서대로 나간다. Gmail이 달력 날짜가 아니라 최근 24시간으로 세므로 상한도 같은 방식으로
     * 센다. 달력 날짜로 세면 자정 전후로 몰렸을 때 24시간 안에 600통까지 나갈 수 있다.
     */
    public static final int EMAIL_LIMIT_PER_WINDOW = 300;

    /** {@link #EMAIL_LIMIT_PER_WINDOW}를 세는 기간이다. 지금 시각에서 이만큼 전까지 보낸 알림 메일을 센다. */
    public static final Duration EMAIL_LIMIT_WINDOW = Duration.ofHours(24);

    private NotificationEventTypes() {}
}
