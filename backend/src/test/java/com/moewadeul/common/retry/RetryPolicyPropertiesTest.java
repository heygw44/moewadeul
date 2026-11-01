package com.moewadeul.common.retry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.notification.application.event.NotificationMailProperties;
import com.moewadeul.outbox.application.OutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryPolicyPropertiesTest {

    @Test
    @DisplayName("outbox 재시도 설정이 잘못되면 moewadeul.outbox 접두어를 붙여 거부한다")
    void rejectsOutboxRetrySettingsWithPrefix() {
        assertThatThrownBy(() -> new OutboxProperties(
                        true,
                        Duration.ofSeconds(1),
                        50,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(30),
                        10,
                        Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.outbox.retryMaxDelay must not be shorter than retryDelay");
        assertThatThrownBy(() -> new OutboxProperties(
                        true,
                        Duration.ofSeconds(1),
                        50,
                        Duration.ZERO,
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        10,
                        Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.outbox.lease must be positive");
    }

    @Test
    @DisplayName("알림 메일 재시도 설정이 잘못되면 moewadeul.notification.mail 접두어를 붙여 거부한다")
    void rejectsNotificationMailRetrySettingsWithPrefix() {
        assertThatThrownBy(() -> new NotificationMailProperties(
                        true,
                        Duration.ofSeconds(1),
                        20,
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        0,
                        Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.notification.mail.maxAttempts must be positive");
    }

    @Test
    @DisplayName("일시 실패 재시도 기간이 0이거나 음수이면 접두어를 붙여 거부한다")
    void rejectsNonPositiveTransientRetryWindow() {
        assertThatThrownBy(() -> new OutboxProperties(
                        true,
                        Duration.ofSeconds(1),
                        25,
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        10,
                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.outbox.transientRetryWindow must be positive");
        assertThatThrownBy(() -> new NotificationMailProperties(
                        true,
                        Duration.ofSeconds(1),
                        20,
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        10,
                        Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.notification.mail.transientRetryWindow must be positive");
    }
}
