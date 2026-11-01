package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.moderation.ReportContentCleanup;
import com.moewadeul.spot.infra.bakji.BakjiReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class BakjiReportContentCleanupService extends ReportContentCleanup {

    private final BakjiReportContentCleanupMapper cleanupMapper;

    BakjiReportContentCleanupService(BakjiReportContentCleanupMapper cleanupMapper, Clock clock) {
        super("bakji", clock);
        this.cleanupMapper = cleanupMapper;
    }

    @Override
    protected int clearContentProcessedBefore(Instant cutoff, int limit) {
        return cleanupMapper.clearContentProcessedBefore(cutoff, limit);
    }
}
