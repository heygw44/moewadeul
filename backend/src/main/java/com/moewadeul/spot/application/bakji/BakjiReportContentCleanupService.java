package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.moderation.ReportContentCleanup;
import com.moewadeul.spot.infra.bakji.BakjiReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BakjiReportContentCleanupService extends ReportContentCleanup {

    private final BakjiReportContentCleanupMapper cleanupMapper;

    BakjiReportContentCleanupService(BakjiReportContentCleanupMapper cleanupMapper, Clock clock) {
        super("bakji", clock);
        this.cleanupMapper = cleanupMapper;
    }

    @Override
    protected List<Long> selectIdsProcessedBefore(Instant cutoff, int limit) {
        return cleanupMapper.selectIdsProcessedBefore(cutoff, limit);
    }

    @Override
    protected int clearContent(List<Long> ids, Instant cutoff) {
        return cleanupMapper.clearContentByIds(ids, cutoff);
    }
}
