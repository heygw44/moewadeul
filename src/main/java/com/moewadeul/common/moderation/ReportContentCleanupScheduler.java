package com.moewadeul.common.moderation;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.moderation.report-content-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReportContentCleanupScheduler {

    private final List<ReportContentCleanup> cleanups;
    private final ScheduledJobRunner jobRunner;

    public ReportContentCleanupScheduler(List<ReportContentCleanup> cleanups, ScheduledJobRunner jobRunner) {
        this.cleanups = cleanups;
        this.jobRunner = jobRunner;
        cleanups.forEach(cleanup -> jobRunner.register(jobName(cleanup)));
    }

    @Scheduled(cron = "${moewadeul.moderation.report-content-cleanup.cron:0 55 4 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        for (ReportContentCleanup cleanup : cleanups) {
            jobRunner.run(jobName(cleanup), cleanup::clearExpiredContent);
        }
    }

    private static String jobName(ReportContentCleanup cleanup) {
        return cleanup.target() + "-report-content-cleanup";
    }
}
