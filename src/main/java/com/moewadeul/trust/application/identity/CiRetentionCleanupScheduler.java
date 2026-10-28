package com.moewadeul.trust.application.identity;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.ci-retention-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CiRetentionCleanupScheduler {

    private static final String JOB_NAME = "ci-retention-cleanup";

    private final CiRetentionCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public CiRetentionCleanupScheduler(
            CiRetentionCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.ci-retention-cleanup.cron:0 40 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteRetentionExpired();
        } catch (Exception e) {
            log.error("ci retention cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
