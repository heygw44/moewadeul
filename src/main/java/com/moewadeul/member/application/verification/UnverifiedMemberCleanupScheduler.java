package com.moewadeul.member.application.verification;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.unverified-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UnverifiedMemberCleanupScheduler {

    private static final String JOB_NAME = "unverified-member-cleanup";

    private final UnverifiedMemberCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public UnverifiedMemberCleanupScheduler(
            UnverifiedMemberCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.unverified-cleanup.cron:0 0 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteExpiredUnverifiedMembers);
    }
}
