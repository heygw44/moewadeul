package com.moewadeul.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxPollingSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09] 아웃박스 발행이 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsFailure() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(publisher).publishPending();

        new OutboxPollingScheduler(publisher, new ScheduledJobRunner(metrics)).poll();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "outbox-polling")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
