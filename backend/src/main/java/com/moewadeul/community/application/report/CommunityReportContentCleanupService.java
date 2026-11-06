package com.moewadeul.community.application.report;

import com.moewadeul.common.moderation.ReportContentCleanup;
import com.moewadeul.community.infra.report.CommunityReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommunityReportContentCleanupService extends ReportContentCleanup {

    private final CommunityReportContentCleanupMapper cleanupMapper;

    CommunityReportContentCleanupService(CommunityReportContentCleanupMapper cleanupMapper, Clock clock) {
        super("community", clock);
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
