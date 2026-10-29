package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.basecamp.contact-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BasecampContactCleanupScheduler {

    private static final String JOB_NAME = "basecamp-contact-cleanup";

    private final BasecampContactCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public BasecampContactCleanupScheduler(
            BasecampContactCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.basecamp.contact-cleanup.cron:0 45 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.clearExpiredContacts();
        } catch (Exception e) {
            log.error("basecamp contact cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
