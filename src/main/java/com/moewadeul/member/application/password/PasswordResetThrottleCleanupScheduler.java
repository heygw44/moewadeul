package com.moewadeul.member.application.password;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.reset-throttle-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PasswordResetThrottleCleanupScheduler {

    private static final String JOB_NAME = "password-reset-throttle-cleanup";

    private final PasswordResetThrottleCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public PasswordResetThrottleCleanupScheduler(
            PasswordResetThrottleCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.reset-throttle-cleanup.cron:0 10 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteStaleThrottles();
        } catch (Exception e) {
            log.error("password reset throttle cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
