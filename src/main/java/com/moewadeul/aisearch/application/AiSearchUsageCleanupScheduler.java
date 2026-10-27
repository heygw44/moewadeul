package com.moewadeul.aisearch.application;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.aisearch.usage-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AiSearchUsageCleanupScheduler {

    private static final String JOB_NAME = "ai-search-usage-cleanup";

    private final AiSearchUsageCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public AiSearchUsageCleanupScheduler(
            AiSearchUsageCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.aisearch.usage-cleanup.cron:0 20 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteExpiredUsage();
        } catch (Exception e) {
            log.error("ai search usage cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
