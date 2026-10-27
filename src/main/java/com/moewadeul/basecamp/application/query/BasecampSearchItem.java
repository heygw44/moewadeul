package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import java.time.LocalDate;

public record BasecampSearchItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        JoinConditionSummary joinCondition,
        JoinEligibility eligibility) {

    public record SpotSummary(long spotId, String name, String type, double lat, double lng) {}
}
