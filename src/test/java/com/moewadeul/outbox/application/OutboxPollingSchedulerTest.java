package com.moewadeul.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.moewadeul.common.mail.MailProperties;
import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxPollingSchedulerTest {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-04] 기본 설정의 임대 시간은 한 묶음을 모두 제한 시간까지 처리하는 시간보다 길어서 시작할 수 있다")
    void defaultsStart() {
        assertThatCode(() -> scheduler(properties(25, Duration.ofMinutes(10)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[NFR-04] 임대 시간이 batchSize × 메일 한 통 제한 시간보다 길지 않으면 시작하지 않는다")
    void rejectsLeaseShorterThanWorstBatch() {
        assertThatThrownBy(() -> scheduler(properties(25, Duration.ofSeconds(500))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("moewadeul.outbox.lease");
    }

    @Test
    @DisplayName("[NFR-09] 아웃박스 발행이 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsFailure() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(publisher).publishPending();

        new OutboxPollingScheduler(
                        publisher,
                        new ScheduledJobRunner(metrics),
                        properties(25, Duration.ofMinutes(10)),
                        mailProperties())
                .poll();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "outbox-polling")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    private OutboxPollingScheduler scheduler(OutboxProperties properties) {
        return new OutboxPollingScheduler(
                mock(OutboxPublisher.class), new ScheduledJobRunner(metrics), properties, mailProperties());
    }

    private static MailProperties mailProperties() {
        return new MailProperties("moewadeul@example.com", "뫼와들", SEND_TIMEOUT);
    }

    private static OutboxProperties properties(int batchSize, Duration lease) {
        return new OutboxProperties(
                true,
                Duration.ofSeconds(1),
                batchSize,
                lease,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                10,
                Duration.ofDays(1));
    }
}
