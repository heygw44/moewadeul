package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasecampAutoTransitionSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09][BC-10] 자동 처리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsAutoTransitionFailure() {
        BasecampAutoTransitionService service = mock(BasecampAutoTransitionService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).run();

        new BasecampAutoTransitionScheduler(service, new ScheduledJobRunner(metrics)).transition();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "basecamp-auto-transition")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][BC-10] 베이스캠프 하나의 처리가 실패해 결과에 실패 수가 있으면 스케줄러는 작업 실패 수를 1 올린다")
    void countsFailureWhenSomeBasecampFailed() {
        BasecampAutoTransitionService service = mock(BasecampAutoTransitionService.class);
        when(service.run()).thenReturn(new BasecampAutoTransitionService.Result(3, 5, 2));

        new BasecampAutoTransitionScheduler(service, new ScheduledJobRunner(metrics)).transition();

        assertThat(failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][BC-10] 실패한 베이스캠프가 없으면 작업 실패 수를 올리지 않는다")
    void doesNotCountFailureWhenNothingFailed() {
        BasecampAutoTransitionService service = mock(BasecampAutoTransitionService.class);
        when(service.run()).thenReturn(new BasecampAutoTransitionService.Result(3, 5, 0));

        new BasecampAutoTransitionScheduler(service, new ScheduledJobRunner(metrics)).transition();

        assertThat(failureCount()).isZero();
    }

    private double failureCount() {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", "basecamp-auto-transition")
                .counter()
                .count();
    }
}
