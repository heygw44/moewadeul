package com.moewadeul.aisearch.application;

import com.moewadeul.common.time.KoreaTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

final class AiSearchDay {

    private AiSearchDay() {}

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(KoreaTime.ZONE));
    }

    static Duration untilNextDay(Instant now, LocalDate date) {
        return Duration.between(
                now, date.plusDays(1).atStartOfDay(KoreaTime.ZONE).toInstant());
    }
}
