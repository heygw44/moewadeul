package com.moewadeul.notification.application.retention;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.notification.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationRetentionScheduler {

    private static final String JOB_NAME = "notification-cleanup";

    private final NotificationRetentionService retentionService;
    private final ScheduledJobRunner jobRunner;

    public NotificationRetentionScheduler(NotificationRetentionService retentionService, ScheduledJobRunner jobRunner) {
        this.retentionService = retentionService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.notification.cleanup.cron:0 35 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, retentionService::deleteExpiredRead);
    }
}
