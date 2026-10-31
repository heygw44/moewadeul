package com.moewadeul.publicdata.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SyncJobLauncherTest {

    private final SyncJobRunService syncJobRunService = mock(SyncJobRunService.class);
    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);

    @Test
    @DisplayName("[F-06] 모든 작업 종류에 작업이 하나씩 등록되어 있으면 런처가 만들어진다")
    void createsLauncherWhenEveryTypeHasOneJob() {
        // given
        List<SyncJob<?>> jobs = jobsOf(SyncJobType.values());

        // when & then
        assertThatCode(() -> new SyncJobLauncher(syncJobRunService, jobs, executor))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[F-06] 작업이 등록되지 않은 작업 종류가 있으면 런처를 만들 때 IllegalStateException이 난다")
    void failsFastWhenTypeHasNoJob() {
        // given
        List<SyncJob<?>> jobs = jobsOf(
                SyncJobType.GOCAMPING, SyncJobType.FOREST, SyncJobType.FOREST_STANDARD, SyncJobType.PARK_BOUNDARY);

        // when & then
        assertThatThrownBy(() -> new SyncJobLauncher(syncJobRunService, jobs, executor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SyncJobType.BAKJI_REJUDGE.name());
    }

    @Test
    @DisplayName("[F-06] 같은 작업 종류의 작업이 둘 이상 등록되어 있으면 런처를 만들 때 IllegalStateException이 난다")
    void failsFastWhenTypeHasDuplicateJobs() {
        // given
        List<SyncJob<?>> jobs = jobsOf(
                SyncJobType.GOCAMPING,
                SyncJobType.FOREST,
                SyncJobType.FOREST_STANDARD,
                SyncJobType.PARK_BOUNDARY,
                SyncJobType.BAKJI_REJUDGE,
                SyncJobType.FOREST);

        // when & then
        assertThatThrownBy(() -> new SyncJobLauncher(syncJobRunService, jobs, executor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SyncJobType.FOREST.name());
    }

    @Test
    @DisplayName("[F-06] 실행기가 작업을 거절하면 방금 만든 실행 기록을 실패가 아닌 거절로 끝내고 예외를 그대로 던진다")
    void rejectedSubmitMarksRunRejected() {
        // given
        SyncJobLauncher launcher = new SyncJobLauncher(syncJobRunService, jobsOf(SyncJobType.values()), executor);
        when(syncJobRunService.begin(eq(SyncJobType.GOCAMPING), any(), anyLong()))
                .thenReturn(Optional.of(new SyncJobRunService.SyncJobStart(7L, Optional.empty())));
        doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));

        // when & then
        assertThatThrownBy(() -> launcher.launch(SyncJobType.GOCAMPING, 1L)).isInstanceOf(TaskRejectedException.class);
        verify(syncJobRunService).reject(7L);
        verify(syncJobRunService, never()).fail(anyLong(), any());
    }

    private static List<SyncJob<?>> jobsOf(SyncJobType... types) {
        return Arrays.stream(types).<SyncJob<?>>map(StubJob::new).toList();
    }

    private record StubJob(SyncJobType type) implements SyncJob<Void> {

        @Override
        public Duration staleAfter() {
            return Duration.ofMinutes(1);
        }

        @Override
        public Void run(SyncJobRunService.SyncJobStart start) {
            return null;
        }
    }
}
