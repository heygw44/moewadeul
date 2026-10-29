package com.moewadeul.common.image.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "moewadeul.community.image-cleanup.enabled=true")
class ImageCleanupSchedulerIntegrationTest {

    private static final String COMMUNITY_CRON = "0 10 5 * * *";
    private static final String REVIEW_CRON = "0 20 5 * * *";
    private static final String MEMORY_CRON = "0 30 5 * * *";
    private static final String FAILURE_COUNTER = "moewadeul.scheduled.job.failures";

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-29] 켠 종류만 설정한 cron을 한국 시간대로 등록하고, 끈 종류는 등록하지 않는다")
    void registersOnlyEnabledKinds() {
        // given
        Instant nineAmSeoul = Instant.parse("2026-11-01T00:00:00Z");

        // when
        List<CronTask> cronTasks = scheduledTaskHolder.getScheduledTasks().stream()
                .map(scheduled -> scheduled.getTask())
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .toList();

        // then
        assertThat(cronTasks).extracting(CronTask::getExpression).contains(COMMUNITY_CRON);
        assertThat(cronTasks).extracting(CronTask::getExpression).doesNotContain(REVIEW_CRON, MEMORY_CRON);
        CronTask community = cronTasks.stream()
                .filter(task -> task.getExpression().equals(COMMUNITY_CRON))
                .findFirst()
                .orElseThrow();
        assertThat(nextExecution(community.getTrigger(), nineAmSeoul)).isEqualTo(Instant.parse("2026-11-01T20:10:00Z"));
        assertThat(meterRegistry
                        .find(FAILURE_COUNTER)
                        .tag("job", "community-image-cleanup")
                        .counter())
                .isNotNull();
        assertThat(meterRegistry
                        .find(FAILURE_COUNTER)
                        .tag("job", "review-image-cleanup")
                        .counter())
                .isNull();
        assertThat(meterRegistry
                        .find(FAILURE_COUNTER)
                        .tag("job", "memory-image-cleanup")
                        .counter())
                .isNull();
    }

    private static Instant nextExecution(Trigger trigger, Instant now) {
        return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(now, ZoneOffset.UTC)));
    }
}
