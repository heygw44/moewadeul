package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.time.LocalDate;

public record MyBasecampRow(
        long basecampId,
        String title,
        long spotId,
        String spotName,
        String spotType,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        BasecampRelation myRelation) {}
