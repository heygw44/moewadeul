package com.moewadeul.community.application.image;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.community.image-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CommunityImageCleanupScheduler {

    private static final String JOB_NAME = "community-image-cleanup";

    private final CommunityImageCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public CommunityImageCleanupScheduler(
            CommunityImageCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.community.image-cleanup.cron:0 10 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteUnattached();
        } catch (Exception e) {
            log.error("community image cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
