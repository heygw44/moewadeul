package com.moewadeul.aisearch.application;

import com.moewadeul.common.time.KoreaTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

final class AiSearchDay {

    private AiSearchDay() {}

    static Duration untilNextDay(Instant now, LocalDate date) {
        return Duration.between(
                now, date.plusDays(1).atStartOfDay(KoreaTime.ZONE).toInstant());
    }
}
