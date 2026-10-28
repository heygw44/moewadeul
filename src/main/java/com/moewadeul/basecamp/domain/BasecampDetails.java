package com.moewadeul.basecamp.domain;

import java.time.LocalDate;

public record BasecampDetails(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Capacity capacity,
        JoinCondition joinCondition) {}
