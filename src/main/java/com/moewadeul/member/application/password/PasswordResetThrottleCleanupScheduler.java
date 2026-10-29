package com.moewadeul.member.application.password;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.reset-throttle-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PasswordResetThrottleCleanupScheduler {

    private static final String JOB_NAME = "password-reset-throttle-cleanup";

    private final PasswordResetThrottleCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public PasswordResetThrottleCleanupScheduler(
            PasswordResetThrottleCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.reset-throttle-cleanup.cron:0 10 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteStaleThrottles);
    }
}
