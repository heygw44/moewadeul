package com.moewadeul.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class KoreaTime {

    public static final String ZONE_ID = "Asia/Seoul";

    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private KoreaTime() {}

    public static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    public static LocalDate today(Clock clock) {
        return dateOf(clock.instant());
    }
}
