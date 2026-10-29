package com.moewadeul.outbox.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.outbox.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventCleanupScheduler {

    private static final String JOB_NAME = "outbox-event-cleanup";

    private final OutboxEventCleaner cleaner;
    private final ScheduledJobRunner jobRunner;

    public OutboxEventCleanupScheduler(OutboxEventCleaner cleaner, ScheduledJobRunner jobRunner) {
        this.cleaner = cleaner;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.outbox.cleanup.cron:0 0 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleaner::deletePublished);
    }
}
