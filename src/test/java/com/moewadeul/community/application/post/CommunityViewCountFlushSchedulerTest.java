package com.moewadeul.community.application.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunityViewCountFlushSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09] 조회수 반영이 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsFailure() {
        CommunityViewCountFlusher flusher = mock(CommunityViewCountFlusher.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(flusher).flush();

        new CommunityViewCountFlushScheduler(flusher, new ScheduledJobRunner(metrics)).flush();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "community-view-count-flush")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
