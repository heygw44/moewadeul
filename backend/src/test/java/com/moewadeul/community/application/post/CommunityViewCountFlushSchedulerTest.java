package com.moewadeul.community.application.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.community.infra.post.CommunityPostMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class CommunityViewCountFlushSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);
    private final CommunityViewCountBuffer buffer = new CommunityViewCountBuffer();
    private final CommunityPostMapper mapper = mock(CommunityPostMapper.class);
    private final CommunityViewCountFlushScheduler scheduler = new CommunityViewCountFlushScheduler(
            new CommunityViewCountFlusher(buffer, mapper), new ScheduledJobRunner(metrics));

    @Test
    @DisplayName("[NFR-09] 조회수 반영이 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsFailure() {
        CommunityViewCountFlusher flusher = mock(CommunityViewCountFlusher.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(flusher).flush();

        new CommunityViewCountFlushScheduler(flusher, new ScheduledJobRunner(metrics)).flush();

        assertThat(failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09] 글 하나의 조회수 반영이 실패하면 작업 실패 수를 1 올리고, 못 더한 조회수는 버퍼에 되돌린다")
    void countsFailureWhenOnePostFailsAndKeepsItsViews() {
        when(mapper.addViewCount(1L, 2L)).thenThrow(new QueryTimeoutException("simulated"));
        buffer.add(1L, 2);
        buffer.add(2L, 1);

        scheduler.flush();

        assertThat(failures()).isEqualTo(1);
        assertThat(buffer.drain()).containsEntry(1L, 2L).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("[NFR-09] 모든 글의 조회수 반영이 성공하면 작업 실패 수는 0으로 남는다")
    void leavesFailureCountZeroWhenAllPostsSucceed() {
        buffer.add(1L, 2);
        buffer.add(2L, 1);

        scheduler.flush();

        assertThat(failures()).isZero();
        assertThat(buffer.drain()).isEmpty();
    }

    private double failures() {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", "community-view-count-flush")
                .counter()
                .count();
    }
}
