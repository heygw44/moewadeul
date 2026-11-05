package com.moewadeul.notification.application.event;

import com.moewadeul.common.retry.RetryPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.notification.mail")
public record NotificationMailProperties(
        @DefaultValue("true") boolean senderEnabled,

        @DefaultValue(NotificationMailProperties.DEFAULT_POLL_DELAY)
        Duration pollDelay,

        @DefaultValue("20") int batchSize,
        @DefaultValue("10m") Duration lease,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("10m") Duration retryMaxDelay,
        @DefaultValue("10") int maxAttempts,
        // 가장 오래가는 일시 실패는 Gmail 하루 한도(지난 24시간 기준)다. 한도가 풀린 뒤에도 재시도 간격만큼 몇 번 더 보내도록 24시간보다 길게 둔다.
        @DefaultValue("26h") Duration transientRetryWindow) {

    static final String DEFAULT_POLL_DELAY = "1s";

    public NotificationMailProperties {
        requirePositive(batchSize, "batchSize");
        requirePositive(pollDelay, "pollDelay");
        RetryPolicy.requireValid(
                "moewadeul.notification.mail", lease, retryDelay, retryMaxDelay, maxAttempts, transientRetryWindow);
    }

    public RetryPolicy retryPolicy() {
        return new RetryPolicy(lease, retryDelay, retryMaxDelay, maxAttempts, transientRetryWindow);
    }

    void requireLeaseOutlastsBatch(Duration sendTimeout) {
        RetryPolicy.requireLeaseOutlastsBatch("moewadeul.notification.mail", lease, batchSize, sendTimeout);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("moewadeul.notification.mail." + name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("moewadeul.notification.mail." + name + " must be positive");
        }
    }
}
