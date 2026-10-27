package com.moewadeul.common.idempotency;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.idempotency.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IdempotencyRecordCleanupScheduler {

    private static final String JOB_NAME = "idempotency-record-cleanup";

    private final IdempotencyRecordCleaner cleaner;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public IdempotencyRecordCleanupScheduler(
            IdempotencyRecordCleaner cleaner, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleaner = cleaner;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.idempotency.cleanup.cron:0 20 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleaner.deleteExpired();
        } catch (Exception e) {
            log.error("idempotency record cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
