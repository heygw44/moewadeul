package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolledMetricsSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobRunner jobRunner = new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry));

    @Test
    @DisplayName("[NFR-09] 한 지표의 읽기가 실패해도 나머지 지표는 읽고, 실패한 작업의 실패 수를 1 올린다")
    void failureOfOneMetricDoesNotSkipTheOthers() {
        FakeMetric failing = new FakeMetric("failing-metrics-refresh", true);
        FakeMetric healthy = new FakeMetric("healthy-metrics-refresh", false);

        new PolledMetricsScheduler(List.of(failing, healthy), jobRunner).refresh();

        assertThat(healthy.refreshes).isEqualTo(1);
        assertThat(failures("failing-metrics-refresh")).isEqualTo(1);
        assertThat(failures("healthy-metrics-refresh")).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 아직 한 번도 실패하지 않은 작업도 실패 수 0으로 먼저 등록해 앱 시작 뒤 첫 실패가 경보에서 빠지지 않게 한다")
    void registersFailureCounterAtStart() {
        new PolledMetricsScheduler(List.of(new FakeMetric("healthy-metrics-refresh", false)), jobRunner);

        assertThat(failures("healthy-metrics-refresh")).isZero();
    }

    private double failures(String job) {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", job)
                .counter()
                .count();
    }

    private static final class FakeMetric implements PolledMetric {

        private final String job;
        private final boolean failing;
        private int refreshes;

        private FakeMetric(String job, boolean failing) {
            this.job = job;
            this.failing = failing;
        }

        @Override
        public String job() {
            return job;
        }

        @Override
        public void refresh() {
            refreshes++;
            if (failing) {
                throw new IllegalStateException("DB 연결 실패");
            }
        }
    }
}
