package com.moewadeul.basecamp.application.query;

import java.time.LocalDate;

public record MyBasecampItem(
        long basecampId,
        String title,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        String myRelation) {

    public record SpotSummary(long spotId, String name, String type) {}
}
