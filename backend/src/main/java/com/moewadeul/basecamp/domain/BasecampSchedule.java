package com.moewadeul.basecamp.domain;

import com.moewadeul.common.schedule.CampingSchedule;
import java.time.LocalDate;

public record BasecampSchedule(long basecampId, LocalDate startDate, LocalDate endDate) {

    public CampingSchedule schedule() {
        return new CampingSchedule(startDate, endDate);
    }
}
