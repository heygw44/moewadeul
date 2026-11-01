package com.moewadeul.publicdata.application;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.SyncJobRunMapper;
import com.moewadeul.publicdata.infra.SyncJobRunRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncJobRunQueryService {

    private final SyncJobRunMapper syncJobRunMapper;

    @Transactional(readOnly = true)
    public PageSlice<SyncJobRunView> findRuns(SyncJobType jobType, int page, int size) {
        List<SyncJobRunRow> rows =
                syncJobRunMapper.selectRuns(jobType, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        return PageSlice.of(rows, page, size).map(SyncJobRunQueryService::toView);
    }

    private static SyncJobRunView toView(SyncJobRunRow row) {
        return new SyncJobRunView(
                row.id(),
                row.jobType(),
                row.status(),
                row.processedCount(),
                row.skippedCount(),
                row.errorMessage(),
                row.startedAt(),
                row.finishedAt());
    }
}
