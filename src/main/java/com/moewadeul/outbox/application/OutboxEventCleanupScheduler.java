package com.moewadeul.outbox.application;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.outbox.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventCleanupScheduler {

    private static final String JOB_NAME = "outbox-event-cleanup";

    private final OutboxEventCleaner cleaner;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public OutboxEventCleanupScheduler(OutboxEventCleaner cleaner, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleaner = cleaner;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.outbox.cleanup.cron:0 0 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleaner.deletePublished();
        } catch (Exception e) {
            log.error("outbox event cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
