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
        @DefaultValue("1d") Duration transientRetryWindow) {

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
