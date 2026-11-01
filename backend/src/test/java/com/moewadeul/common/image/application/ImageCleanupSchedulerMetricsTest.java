package com.moewadeul.common.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class ImageCleanupSchedulerMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ImageCleanup cleanup = mock(ImageCleanup.class);

    @Test
    @DisplayName("[NFR-09] 저장소 삭제 실패가 남은 채 정리가 끝나면 작업 실패 수를 1 올린다")
    void countsFailureWhenStorageDeleteFailed() {
        runCleanupReturning(new ImageCleanup.Result(3, 2));

        assertThat(failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09] 저장소 삭제 실패 없이 정리가 끝나면 작업 실패 수는 0으로 남는다")
    void leavesFailureCountZeroWhenCleanupSucceeded() {
        runCleanupReturning(new ImageCleanup.Result(3, 0));

        assertThat(failures()).isZero();
    }

    private void runCleanupReturning(ImageCleanup.Result result) {
        when(cleanup.keyPrefix()).thenReturn("community");
        when(cleanup.deleteExpiredRowsThenObjects()).thenReturn(result);
        MockEnvironment environment =
                new MockEnvironment().withProperty("moewadeul.community.image-cleanup.cron", "0 10 5 * * *");
        ImageCleanupScheduler scheduler = new ImageCleanupScheduler(
                List.of(cleanup), new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry)), environment);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private double failures() {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", "community-image-cleanup")
                .counter()
                .count();
    }
}
