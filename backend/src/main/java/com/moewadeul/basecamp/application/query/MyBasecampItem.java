package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.time.LocalDate;

public record MyBasecampItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        BasecampRelation myRelation) {

    public record SpotSummary(long spotId, String name, BasecampSpotType type) {}
}
