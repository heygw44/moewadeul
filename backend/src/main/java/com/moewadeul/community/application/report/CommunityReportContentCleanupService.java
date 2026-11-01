package com.moewadeul.community.application.report;

import com.moewadeul.common.moderation.ReportContentCleanup;
import com.moewadeul.community.infra.report.CommunityReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CommunityReportContentCleanupService extends ReportContentCleanup {

    private final CommunityReportContentCleanupMapper cleanupMapper;

    CommunityReportContentCleanupService(CommunityReportContentCleanupMapper cleanupMapper, Clock clock) {
        super("community", clock);
        this.cleanupMapper = cleanupMapper;
    }

    @Override
    protected int clearContentProcessedBefore(Instant cutoff, int limit) {
        return cleanupMapper.clearContentProcessedBefore(cutoff, limit);
    }
}
