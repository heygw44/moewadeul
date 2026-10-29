package com.moewadeul.memory.application.image;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.memory.image-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MemoryImageCleanupScheduler {

    private static final String JOB_NAME = "memory-image-cleanup";

    private final MemoryImageCleanupService cleanupService;
    private final ScheduledJobRunner jobRunner;

    public MemoryImageCleanupScheduler(MemoryImageCleanupService cleanupService, ScheduledJobRunner jobRunner) {
        this.cleanupService = cleanupService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.memory.image-cleanup.cron:0 30 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        jobRunner.run(JOB_NAME, cleanupService::deleteExpiredRowsThenObjects);
    }
}
