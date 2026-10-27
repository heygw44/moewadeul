package com.moewadeul.publicdata.application.gocamping;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.publicdata.gocamping.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GoCampingSyncScheduler {

    private static final String JOB_NAME = "gocamping-sync";

    private final GoCampingSyncService goCampingSyncService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public GoCampingSyncScheduler(GoCampingSyncService goCampingSyncService, ScheduledJobMetrics scheduledJobMetrics) {
        this.goCampingSyncService = goCampingSyncService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.publicdata.gocamping.sync.cron:0 0 3 * * *}", zone = KoreaTime.ZONE_ID)
    public void sync() {
        try {
            goCampingSyncService.sync();
        } catch (Exception e) {
            log.error("gocamping sync failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
