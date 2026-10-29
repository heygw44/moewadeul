package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.MyBasecampItem;
import java.time.LocalDate;

public record MyBasecampResponse(
        long basecampId,
        String title,
        SpotEntry spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        String myRelation) {

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

    public record SpotEntry(long spotId, String name, String type) {}
}
