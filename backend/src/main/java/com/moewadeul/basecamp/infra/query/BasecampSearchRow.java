package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.common.profile.Gender;
import java.time.LocalDate;

public record BasecampSearchRow(
        long basecampId,
        String title,
        long spotId,
        String spotName,
        BasecampSpotType spotType,
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
        Gender requiredGender) {}
