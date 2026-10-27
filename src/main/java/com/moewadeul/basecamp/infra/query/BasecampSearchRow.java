package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.domain.JoinGender;
import java.time.LocalDate;

public record BasecampSearchRow(
        long basecampId,
        String title,
        long spotId,
        String spotName,
        String spotType,
        double spotLat,
        double spotLng,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        Integer minTrustLevel,
        Integer ageGroupMin,
        Integer ageGroupMax,
        boolean sameGenderOnly,
        JoinGender requiredGender) {}
