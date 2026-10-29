package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.infra.query.MyBasecampMapper;
import com.moewadeul.basecamp.infra.query.MyBasecampRow;
import com.moewadeul.common.paging.PageSlice;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyBasecampListService {

    private final MyBasecampMapper myBasecampMapper;

    @Transactional(readOnly = true)
    public PageSlice<MyBasecampItem> list(long memberId, MyBasecampListQuery query) {
        List<MyBasecampRow> rows = myBasecampMapper.selectMine(
                memberId,
                query.relation(),
                query.status(),
                PageSlice.offsetOf(query.page(), query.size()),
                PageSlice.fetchSize(query.size()));
        return PageSlice.of(rows, query.page(), query.size()).map(MyBasecampListService::toItem);
    }

    private static MyBasecampItem toItem(MyBasecampRow row) {
        return new MyBasecampItem(
                row.basecampId(),
                row.title(),
                new MyBasecampItem.SpotSummary(row.spotId(), row.spotName(), row.spotType()),
                row.startDate(),
                row.endDate(),
                row.capacity(),
                row.headcount(),
                row.status(),
                row.myRelation());
    }
}
