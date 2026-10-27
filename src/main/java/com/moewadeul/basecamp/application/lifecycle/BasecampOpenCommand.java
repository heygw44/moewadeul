package com.moewadeul.basecamp.application.lifecycle;

import java.time.LocalDate;

public record BasecampOpenCommand(
        long spotId,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        JoinConditionCommand joinCondition) {

    public record JoinConditionCommand(
            Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
}
