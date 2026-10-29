package com.moewadeul.notification.application.event;

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

class NotificationMailSchedulerTest {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);

    @Test
    @DisplayName("기본 설정의 임대 시간은 한 묶음을 모두 제한 시간까지 보내는 시간보다 길어서 시작할 수 있다")
    void defaultsStart() {
        assertThatCode(() -> scheduler(properties(20, Duration.ofMinutes(10)), SEND_TIMEOUT, 4))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("임대 시간이 batchSize × 메일 한 통 제한 시간보다 길지 않으면 시작하지 않는다")
    void rejectsLeaseShorterThanWorstBatch() {
        assertThatThrownBy(() -> scheduler(properties(20, Duration.ofSeconds(400)), SEND_TIMEOUT, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease");
    }

    @Test
    @DisplayName("스케줄러 스레드가 하나뿐이면 아웃박스 발행과 스레드를 나눠 쓰게 되므로 시작하지 않는다")
    void rejectsSingleSchedulerThread() {
        assertThatThrownBy(() -> scheduler(properties(20, Duration.ofMinutes(10)), SEND_TIMEOUT, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.task.scheduling.pool.size");
    }

    @Test
    @DisplayName("[NFR-09] 알림 메일 발송이 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsSendFailure() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationMailer mailer = mock(NotificationMailer.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(mailer).sendDue();

        new NotificationMailScheduler(
                        mailer,
                        jobRunner(meterRegistry),
                        properties(20, Duration.ofMinutes(10)),
                        mailProperties(SEND_TIMEOUT),
                        4)
                .send();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "notification-mail")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    private static NotificationMailScheduler scheduler(
            NotificationMailProperties properties, Duration sendTimeout, int poolSize) {
        return new NotificationMailScheduler(
                mock(NotificationMailer.class),
                jobRunner(new SimpleMeterRegistry()),
                properties,
                mailProperties(sendTimeout),
                poolSize);
    }

    private static MailProperties mailProperties(Duration sendTimeout) {
        return new MailProperties("moewadeul@example.com", "뫼와들", sendTimeout);
    }

    private static ScheduledJobRunner jobRunner(SimpleMeterRegistry meterRegistry) {
        return new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry));
    }

    private static NotificationMailProperties properties(int batchSize, Duration lease) {
        return new NotificationMailProperties(
                true, Duration.ofSeconds(1), batchSize, lease, Duration.ofSeconds(30), Duration.ofMinutes(10), 10);
    }
}
