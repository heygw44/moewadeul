package com.moewadeul.memory.application.report;

import com.moewadeul.common.moderation.ReportContentCleanup;
import com.moewadeul.memory.infra.report.MemoryReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class MemoryReportContentCleanupService extends ReportContentCleanup {

    private final MemoryReportContentCleanupMapper cleanupMapper;

    MemoryReportContentCleanupService(MemoryReportContentCleanupMapper cleanupMapper, Clock clock) {
        super("memory", clock);
        this.cleanupMapper = cleanupMapper;
    }

    @Override
    protected int clearContentProcessedBefore(Instant cutoff, int limit) {
        return cleanupMapper.clearContentProcessedBefore(cutoff, limit);
    }
}
