package com.moewadeul.common.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record CampingSchedule(LocalDate startDate, LocalDate endDate) {

    public static final int MAX_DAYS_UNTIL_START = 60;

    public static final int MIN_NIGHTS = 1;
    public static final int MAX_NIGHTS = 3;

    public long nights() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    public boolean isBookableOn(LocalDate today) {
        if (startDate == null || endDate == null) {
            return false;
        }
        long daysUntilStart = ChronoUnit.DAYS.between(today, startDate);
        long nights = nights();
        return daysUntilStart >= 1
                && daysUntilStart <= MAX_DAYS_UNTIL_START
                && nights >= MIN_NIGHTS
                && nights <= MAX_NIGHTS;
    }

    public boolean sharesDateWith(CampingSchedule other) {
        return !startDate.isAfter(other.endDate) && !endDate.isBefore(other.startDate);
    }

    public boolean sharesNightWith(CampingSchedule other) {
        return startDate.isBefore(other.endDate) && other.startDate.isBefore(endDate);
    }
}
