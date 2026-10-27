package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.basecamp.auto-transition",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BasecampAutoTransitionScheduler {

    private static final String JOB_NAME = "basecamp-auto-transition";

    private final BasecampAutoTransitionService autoTransitionService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public BasecampAutoTransitionScheduler(
            BasecampAutoTransitionService autoTransitionService, ScheduledJobMetrics scheduledJobMetrics) {
        this.autoTransitionService = autoTransitionService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.basecamp.auto-transition.cron:0 0/10 0 * * *}", zone = KoreaTime.ZONE_ID)
    public void transition() {
        try {
            BasecampAutoTransitionService.Result result = autoTransitionService.run();
            if (result.failed() > 0) {
                scheduledJobMetrics.recordFailure(JOB_NAME);
            }
        } catch (Exception e) {
            log.error("basecamp auto transition failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
