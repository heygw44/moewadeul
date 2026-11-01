package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.basecamp.contact-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BasecampContactCleanupScheduler {

    private static final String JOB_NAME = "basecamp-contact-cleanup";

    private final BasecampContactCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public BasecampContactCleanupScheduler(BasecampContactCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.basecamp.contact-cleanup.cron:0 45 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::clearExpiredContacts);
    }
}
