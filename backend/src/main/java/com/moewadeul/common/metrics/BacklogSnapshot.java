package com.moewadeul.common.metrics;

import java.time.Duration;
import java.time.Instant;

public record BacklogSnapshot(long pending, long failed, double oldestPendingAgeSeconds) {

    public static BacklogSnapshot at(Instant now, long pending, long failed, Instant oldestPendingCreatedAt) {
        if (oldestPendingCreatedAt == null) {
            return new BacklogSnapshot(pending, failed, 0);
        }
        double ageSeconds =
                Math.max(0, Duration.between(oldestPendingCreatedAt, now).toMillis() / 1000.0);
        return new BacklogSnapshot(pending, failed, ageSeconds);
    }
}
