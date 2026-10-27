package com.moewadeul.spot.application.detail;

import java.time.LocalDate;

public record SpotRecruitingBasecamp(
        long basecampId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        SpotJoinCondition joinCondition) {}
