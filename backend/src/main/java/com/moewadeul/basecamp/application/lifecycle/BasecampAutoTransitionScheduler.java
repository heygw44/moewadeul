package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.basecamp.auto-transition",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BasecampAutoTransitionScheduler {

    private static final String JOB_NAME = "basecamp-auto-transition";

    private final BasecampAutoTransitionService autoTransitionService;
    private final ScheduledJobRunner jobRunner;

    public BasecampAutoTransitionScheduler(
            BasecampAutoTransitionService autoTransitionService, ScheduledJobRunner jobRunner) {
        this.autoTransitionService = autoTransitionService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.basecamp.auto-transition.cron:0 0/10 0 * * *}", zone = KoreaTime.ZONE_ID)
    public void transition() {
        jobRunner.runCountingFailures(
                JOB_NAME, () -> autoTransitionService.run().failed());
    }
}
