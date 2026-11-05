package com.moewadeul.common.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModerationReportContentCleanupSchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobRunner runner = new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry));
    private final List<String> ran = new ArrayList<>();

    @Test
    @DisplayName("[NFR-09][PV-07] 대상 하나가 실패해도 나머지 대상의 신고 내용을 지우고, 실패는 그 대상의 작업 실패로만 센다")
    void continuesAfterOneTargetFails() {
        new ModerationReportContentCleanupScheduler(
                        List.of(cleanup("community", false), cleanup("memory", true), cleanup("bakji", false)), runner)
                .cleanUp();

        assertThat(ran).containsExactly("community", "memory", "bakji");
        assertThat(failures("community-report-content-cleanup")).isZero();
        assertThat(failures("memory-report-content-cleanup")).isEqualTo(1);
        assertThat(failures("bakji-report-content-cleanup")).isZero();
    }

    private ReportContentCleanup cleanup(String target, boolean fails) {
        return new ReportContentCleanup(target, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) {
            @Override
            protected int clearContentProcessedBefore(Instant cutoff, int limit) {
                ran.add(target);
                if (fails) {
                    throw new IllegalStateException("DB 연결 실패");
                }
                return 0;
            }
        };
    }

    private double failures(String job) {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", job)
                .counter()
                .count();
    }
}
