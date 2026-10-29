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
                        10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.outbox.retryMaxDelay must not be shorter than retryDelay");
        assertThatThrownBy(() -> new OutboxProperties(
                        true,
                        Duration.ofSeconds(1),
                        50,
                        Duration.ZERO,
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        10))
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
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.notification.mail.maxAttempts must be positive");
    }
}
