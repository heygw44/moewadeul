package com.moewadeul.common.metrics;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.metrics",
        name = "refresh-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PolledMetricsScheduler {

    private final List<PolledMetric> metrics;
    private final ScheduledJobRunner jobRunner;

    public PolledMetricsScheduler(List<PolledMetric> metrics, ScheduledJobRunner jobRunner) {
        this.metrics = metrics;
        this.jobRunner = jobRunner;
        metrics.forEach(metric -> jobRunner.register(metric.job()));
    }

    @Scheduled(fixedDelay = PolledSnapshot.REFRESH_DELAY_MILLIS)
    public void refresh() {
        for (PolledMetric metric : metrics) {
            jobRunner.run(metric.job(), metric::refresh);
        }
    }
}
