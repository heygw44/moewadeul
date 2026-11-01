package com.moewadeul.publicdata.application.gocamping;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.publicdata.gocamping.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GoCampingSyncScheduler {

    private static final String JOB_NAME = "gocamping-sync";

    private final GoCampingSyncService goCampingSyncService;
    private final ScheduledJobRunner jobRunner;

    public GoCampingSyncScheduler(GoCampingSyncService goCampingSyncService, ScheduledJobRunner jobRunner) {
        this.goCampingSyncService = goCampingSyncService;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.publicdata.gocamping.sync.cron:0 0 3 * * *}", zone = KoreaTime.ZONE_ID)
    public void sync() {
        jobRunner.run(JOB_NAME, goCampingSyncService::sync);
    }
}
