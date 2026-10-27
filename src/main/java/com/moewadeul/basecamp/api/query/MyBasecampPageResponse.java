package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.MyBasecampItem;
import com.moewadeul.basecamp.application.query.MyBasecampPage;
import java.time.LocalDate;
import java.util.List;

public record MyBasecampPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static MyBasecampPageResponse from(MyBasecampPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new MyBasecampPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record Entry(
            long basecampId,
            String title,
            SpotEntry spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            String myRelation) {

        static Entry from(MyBasecampItem item) {
            return new Entry(
                    item.basecampId(),
                    item.title(),
                    new SpotEntry(
                            item.spot().spotId(),
                            item.spot().name(),
                            item.spot().type()),
                    item.startDate(),
                    item.endDate(),
                    item.capacity(),
                    item.headcount(),
                    item.status(),
                    item.myRelation());
        }
    }

    public record SpotEntry(long spotId, String name, String type) {}
}
