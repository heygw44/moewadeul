package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.program.payment-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProgramPaymentExpiryScheduler {

    private static final String JOB_NAME = "program-payment-expiry";

    private final ProgramPaymentExpiryService expiryService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public ProgramPaymentExpiryScheduler(
            ProgramPaymentExpiryService expiryService, ScheduledJobMetrics scheduledJobMetrics) {
        this.expiryService = expiryService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.program.payment-expiry.cron:0 * * * * *}", zone = KoreaTime.ZONE_ID)
    public void expire() {
        try {
            ProgramPaymentExpiryService.Result result = expiryService.run();
            if (result.failed() > 0) {
                scheduledJobMetrics.recordFailure(JOB_NAME);
            }
        } catch (Exception e) {
            log.error("program payment expiry failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
