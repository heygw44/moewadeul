package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.trust.sanction-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SanctionExpiryScheduler {

    private static final String JOB_NAME = "sanction-expiry";

    private final SanctionExpiryService sanctionExpiryService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public SanctionExpiryScheduler(
            SanctionExpiryService sanctionExpiryService, ScheduledJobMetrics scheduledJobMetrics) {
        this.sanctionExpiryService = sanctionExpiryService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.trust.sanction-expiry.cron:0 */10 * * * *}", zone = KoreaTime.ZONE_ID)
    public void expire() {
        try {
            sanctionExpiryService.run();
        } catch (Exception e) {
            log.error("sanction expiry failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
