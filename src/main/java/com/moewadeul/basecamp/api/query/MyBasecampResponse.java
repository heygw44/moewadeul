package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.application.query.MyBasecampItem;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.time.LocalDate;

public record MyBasecampResponse(
        long basecampId,
        String title,
        SpotEntry spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        BasecampRelation myRelation) {

    static MyBasecampResponse from(MyBasecampItem item) {
        return new MyBasecampResponse(
                item.basecampId(),
                item.title(),
                new SpotEntry(
                        item.spot().spotId(), item.spot().name(), item.spot().type()),
                item.startDate(),
                item.endDate(),
                item.capacity(),
                item.headcount(),
                item.status(),
                item.myRelation());
    }

    public record SpotEntry(long spotId, String name, BasecampSpotType type) {}
}
