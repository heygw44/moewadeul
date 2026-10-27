package com.moewadeul.trust.application.report;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.report-content-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReportContentCleanupScheduler {

    private static final String JOB_NAME = "report-content-cleanup";

    private final ReportContentCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public ReportContentCleanupScheduler(
            ReportContentCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.report-content-cleanup.cron:0 50 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.clearExpiredContent();
        } catch (Exception e) {
            log.error("report content cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
