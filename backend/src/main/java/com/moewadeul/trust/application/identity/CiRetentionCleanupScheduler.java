package com.moewadeul.trust.application.identity;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.ci-retention-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CiRetentionCleanupScheduler {

    private static final String JOB_NAME = "ci-retention-cleanup";

    private final CiRetentionCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public CiRetentionCleanupScheduler(CiRetentionCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.ci-retention-cleanup.cron:0 40 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteRetentionExpired);
    }
}
