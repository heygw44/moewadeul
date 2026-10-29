package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.program.payment-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProgramPaymentExpiryScheduler {

    private static final String JOB_NAME = "program-payment-expiry";

    private final ProgramPaymentExpiryService expiryService;
    private final ScheduledJobRunner jobRunner;

    public ProgramPaymentExpiryScheduler(ProgramPaymentExpiryService expiryService, ScheduledJobRunner jobRunner) {
        this.expiryService = expiryService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.program.payment-expiry.cron:0 * * * * *}", zone = KoreaTime.ZONE_ID)
    public void expire() {
        jobRunner.runCountingFailures(JOB_NAME, () -> expiryService.run().failed());
    }
}
