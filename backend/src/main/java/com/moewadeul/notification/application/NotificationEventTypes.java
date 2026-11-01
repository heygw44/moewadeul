package com.moewadeul.notification.application;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.event.TrustEvents;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class NotificationEventTypes {

    public static final List<String> EMAIL_CONFIGURABLE = List.of(
            BasecampEvents.APPLIED,
            BasecampEvents.APPROVED,
            BasecampEvents.REJECTED,
            BasecampEvents.KICKED,
            BasecampEvents.CONFIRMED,
            BasecampEvents.CANCELED,
            BasecampEvents.COMPLETED,
            BasecampEvents.MEMBER_CHANGED,
            TrustEvents.REPORT_RESOLVED,
            TrustEvents.SANCTION_CONFIRMED,
            ProgramEvents.APPLICATION_CONFIRMED,
            ProgramEvents.APPLICATION_CANCELED,
            ProgramEvents.APPLICATION_EXPIRED,
            ProgramEvents.SEAT_RELEASED);

    public static final boolean DEFAULT_EMAIL_ENABLED = true;

    public static final Map<String, Integer> EMAIL_LIMIT_PER_EVENT = Map.of(ProgramEvents.SEAT_RELEASED, 50);

    /**
     * 운영 메일 계정(Gmail)은 최근 24시간에 약 500통까지만 보낸다. 알림 메일이 이 한도를 다 쓰면 인증 코드와 비밀번호 재설정 메일이
     * 나가지 못한다. 그래서 알림 메일은 300통까지만 보내고, 나머지 200통은 인증 코드와 재설정 메일 몫으로 남긴다.
     * Gmail이 달력 날짜가 아니라 최근 24시간으로 세므로 상한도 같은 방식으로 센다.
     */
    public static final int EMAIL_LIMIT_PER_WINDOW = 300;

    public static final Duration EMAIL_LIMIT_WINDOW = Duration.ofHours(24);

    private NotificationEventTypes() {}
}
