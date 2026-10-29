package com.moewadeul.community.application.image;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.community.image-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CommunityImageCleanupScheduler {

    private static final String JOB_NAME = "community-image-cleanup";

    private final CommunityImageCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public CommunityImageCleanupScheduler(CommunityImageCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.community.image-cleanup.cron:0 10 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteExpiredRowsThenObjects);
    }
}
