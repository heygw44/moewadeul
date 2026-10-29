package com.moewadeul.idempotency.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.idempotency.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IdempotencyRecordCleanupScheduler {

    private static final String JOB_NAME = "idempotency-record-cleanup";

    private final IdempotencyRecordCleaner cleaner;
    private final ScheduledJobRunner jobRunner;

    public IdempotencyRecordCleanupScheduler(IdempotencyRecordCleaner cleaner, ScheduledJobRunner jobRunner) {
        this.cleaner = cleaner;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.idempotency.cleanup.cron:0 20 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleaner::deleteExpired);
    }
}
