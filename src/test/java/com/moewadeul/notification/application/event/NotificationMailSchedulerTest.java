package com.moewadeul.notification.application.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    private static NotificationMailScheduler scheduler(
            NotificationMailProperties properties, Duration sendTimeout, int poolSize) {
        return new NotificationMailScheduler(mock(NotificationMailer.class), properties, sendTimeout, poolSize);
    }

    private static NotificationMailProperties properties(int batchSize, Duration lease) {
        return new NotificationMailProperties(
                true, Duration.ofSeconds(1), batchSize, lease, Duration.ofSeconds(30), Duration.ofMinutes(10), 10);
    }
}
