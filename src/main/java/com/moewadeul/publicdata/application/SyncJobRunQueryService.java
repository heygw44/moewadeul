package com.moewadeul.publicdata.application;

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
    public SyncJobRunPage findRuns(SyncJobType jobType, int page, int size) {
        long offset = (long) page * size;
        List<SyncJobRunRow> rows = syncJobRunMapper.selectRuns(jobType, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<SyncJobRunView> content =
                rows.stream().limit(size).map(SyncJobRunQueryService::toView).toList();
        return new SyncJobRunPage(content, page, size, hasNext);
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
