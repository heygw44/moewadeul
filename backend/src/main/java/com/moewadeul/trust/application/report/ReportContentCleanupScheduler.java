package com.moewadeul.trust.application.report;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.report-content-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReportContentCleanupScheduler {

    private static final String JOB_NAME = "report-content-cleanup";

    private final ReportContentCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public ReportContentCleanupScheduler(ReportContentCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.report-content-cleanup.cron:0 50 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::clearExpiredContent);
    }
}
