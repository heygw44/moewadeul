package com.moewadeul.memory.application.image;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.memory.image-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MemoryImageCleanupScheduler {

    private static final String JOB_NAME = "memory-image-cleanup";

    private final MemoryImageCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public MemoryImageCleanupScheduler(
            MemoryImageCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.memory.image-cleanup.cron:0 30 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteUnattached();
        } catch (Exception e) {
            log.error("memory image cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
