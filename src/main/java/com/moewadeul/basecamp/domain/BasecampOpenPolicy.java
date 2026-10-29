package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.BusinessException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public final class BasecampOpenPolicy {

    public static final int MIN_TRUST_LEVEL = 1;

    public static final int MAX_OPEN_PER_LEADER = 3;

    public static final Set<BasecampStatus> OPEN_COUNTED_STATUSES =
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);

    public static final int MAX_DAYS_UNTIL_START = 60;

    public static final int MIN_NIGHTS = 1;
    public static final int MAX_NIGHTS = 3;

    private BasecampOpenPolicy() {}

    public static void requireSchedule(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID);
        }
        long daysUntilStart = ChronoUnit.DAYS.between(today, startDate);
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        boolean startInRange = daysUntilStart >= 1 && daysUntilStart <= MAX_DAYS_UNTIL_START;
        boolean nightsInRange = nights >= MIN_NIGHTS && nights <= MAX_NIGHTS;
        if (!startInRange || !nightsInRange) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID);
        }
    }

    public static void requireUnderOpenLimit(long openCount) {
        if (openCount >= MAX_OPEN_PER_LEADER) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_OPEN_LIMIT);
        }
    }
}
