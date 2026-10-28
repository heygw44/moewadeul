package com.moewadeul.spot.infra.detail;

import java.time.LocalDate;

public record SpotRecruitingBasecampRow(
        long basecampId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        Integer minTrustLevel,
        Integer ageGroupMin,
        Integer ageGroupMax,
        boolean sameGenderOnly) {}
