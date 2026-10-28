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
        prefix = "moewadeul.member.unverified-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UnverifiedMemberCleanupScheduler {

    private static final String JOB_NAME = "unverified-member-cleanup";

    private final UnverifiedMemberCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public UnverifiedMemberCleanupScheduler(
            UnverifiedMemberCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.unverified-cleanup.cron:0 0 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteExpiredUnverifiedMembers();
        } catch (Exception e) {
            log.error("unverified member cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
