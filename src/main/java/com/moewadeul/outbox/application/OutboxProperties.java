package com.moewadeul.outbox.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean publisherEnabled,
        @DefaultValue(OutboxProperties.DEFAULT_POLL_DELAY) Duration pollDelay,
        @DefaultValue("50") int batchSize,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("10m") Duration retryMaxDelay,
        @DefaultValue("10") int maxAttempts) {

    static final String DEFAULT_POLL_DELAY = "1s";

    public OutboxProperties {
        requirePositive(batchSize, "batchSize");
        requirePositive(maxAttempts, "maxAttempts");
        requirePositive(pollDelay, "pollDelay");
        requirePositive(lease, "lease");
        requirePositive(retryDelay, "retryDelay");
        requirePositive(retryMaxDelay, "retryMaxDelay");
        if (retryMaxDelay.compareTo(retryDelay) < 0) {
            throw new IllegalArgumentException("moewadeul.outbox.retryMaxDelay must not be shorter than retryDelay");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("moewadeul.outbox." + name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("moewadeul.outbox." + name + " must be positive");
        }
    }
}
