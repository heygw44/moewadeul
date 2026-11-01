package com.moewadeul.member.application.login;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.login-history-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LoginHistoryCleanupScheduler {

    private static final String JOB_NAME = "login-history-cleanup";

    private final LoginHistoryCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public LoginHistoryCleanupScheduler(LoginHistoryCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.login-history-cleanup.cron:0 30 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteExpired);
    }
}
