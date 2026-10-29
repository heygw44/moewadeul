package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.sanction-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SanctionExpiryScheduler {

    private static final String JOB_NAME = "sanction-expiry";

    private final SanctionExpiryService sanctionExpiryService;
    private final ScheduledJobRunner jobRunner;

    public SanctionExpiryScheduler(SanctionExpiryService sanctionExpiryService, ScheduledJobRunner jobRunner) {
        this.sanctionExpiryService = sanctionExpiryService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.sanction-expiry.cron:0 */10 * * * *}", zone = KoreaTime.ZONE_ID)
    public void expire() {
        jobRunner.run(JOB_NAME, sanctionExpiryService::run);
    }
}
