package com.moewadeul.member.application.verification;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.email-verification-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailVerificationCleanupScheduler {

    private static final String JOB_NAME = "email-verification-cleanup";

    private final EmailVerificationCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public EmailVerificationCleanupScheduler(
            EmailVerificationCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.email-verification-cleanup.cron:0 40 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteExpired();
        } catch (Exception e) {
            log.error("email verification cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
