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

    /**
     * 호출하면 실행 기록을 최근 것부터 한 페이지 돌려준다. {@code jobType}이 null이면 모든 종류를 돌려준다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
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
