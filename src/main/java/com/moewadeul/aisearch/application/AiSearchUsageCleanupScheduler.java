package com.moewadeul.aisearch.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.aisearch.usage-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AiSearchUsageCleanupScheduler {

    private static final String JOB_NAME = "ai-search-usage-cleanup";

    private final AiSearchUsageCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public AiSearchUsageCleanupScheduler(AiSearchUsageCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.aisearch.usage-cleanup.cron:0 20 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteExpiredUsage);
    }
}
