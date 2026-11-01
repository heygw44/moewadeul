package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.time.LocalDate;

public record BasecampSearchItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        JoinConditionSummary joinCondition,
        JoinEligibility eligibility) {

    public record SpotSummary(long spotId, String name, BasecampSpotType type, double lat, double lng) {}
}
