package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.infra.query.MyBasecampMapper;
import com.moewadeul.basecamp.infra.query.MyBasecampRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyBasecampListService {

    private final MyBasecampMapper myBasecampMapper;

    @Transactional(readOnly = true)
    public MyBasecampPage list(long memberId, MyBasecampListQuery query) {
        long offset = (long) query.page() * query.size();
        List<MyBasecampRow> rows =
                myBasecampMapper.selectMine(memberId, query.relation(), query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<MyBasecampItem> content = rows.stream()
                .limit(query.size())
                .map(MyBasecampListService::toItem)
                .toList();
        return new MyBasecampPage(content, query.page(), query.size(), hasNext);
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
                row.status().name(),
                row.myRelation().name());
    }
}
