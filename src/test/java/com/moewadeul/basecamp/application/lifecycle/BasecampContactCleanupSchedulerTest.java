package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasecampContactCleanupSchedulerTest {

    @Test
    @DisplayName("[NFR-09][PV-13] 연락 수단 정리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsCleanupFailure() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BasecampContactCleanupService service = mock(BasecampContactCleanupService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).clearExpiredContacts();

        new BasecampContactCleanupScheduler(service, new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry)))
                .cleanUp();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "basecamp-contact-cleanup")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
