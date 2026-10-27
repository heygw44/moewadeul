package com.moewadeul.member.application.login;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.login-history-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LoginHistoryCleanupScheduler {

    private static final String JOB_NAME = "login-history-cleanup";

    private final LoginHistoryCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public LoginHistoryCleanupScheduler(
            LoginHistoryCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.login-history-cleanup.cron:0 30 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteExpired();
        } catch (Exception e) {
            log.error("login history cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
