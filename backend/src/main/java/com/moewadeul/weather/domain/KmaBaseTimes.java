package com.moewadeul.weather.domain;

import com.moewadeul.common.time.KoreaTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public final class KmaBaseTimes {

    private static final Duration PUBLISH_DELAY = Duration.ofMinutes(10);
    private static final List<LocalTime> SHORT_TERM_TIMES = hours(2, 5, 8, 11, 14, 17, 20, 23);
    private static final List<LocalTime> MID_TERM_TIMES = hours(6, 18);

    private KmaBaseTimes() {}

    public static LocalDateTime shortTermBase(Instant now) {
        return latestAvailable(now, SHORT_TERM_TIMES);
    }

    public static LocalDateTime midTermBase(Instant now) {
        return latestAvailable(now, MID_TERM_TIMES);
    }

    private static LocalDateTime latestAvailable(Instant now, List<LocalTime> publishTimes) {
        LocalDateTime kst = LocalDateTime.ofInstant(now, KoreaTime.ZONE);
        LocalDate date = kst.toLocalDate();
        for (int i = publishTimes.size() - 1; i >= 0; i--) {
            LocalDateTime publishedAt = date.atTime(publishTimes.get(i));
            if (!kst.isBefore(publishedAt.plus(PUBLISH_DELAY))) {
                return publishedAt;
            }
        }
        return date.minusDays(1).atTime(publishTimes.getLast());
    }

    private static List<LocalTime> hours(int... hours) {
        return Arrays.stream(hours).mapToObj(h -> LocalTime.of(h, 0)).toList();
    }
}
