package com.moewadeul.review.application;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.review.image-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReviewImageCleanupScheduler {

    private static final String JOB_NAME = "review-image-cleanup";

    private final ReviewImageCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public ReviewImageCleanupScheduler(
            ReviewImageCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.review.image-cleanup.cron:0 20 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteUnattached();
        } catch (Exception e) {
            log.error("review image cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
