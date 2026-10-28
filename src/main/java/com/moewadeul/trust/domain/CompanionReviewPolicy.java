package com.moewadeul.trust.domain;

import java.time.Duration;
import java.time.Instant;

public final class CompanionReviewPolicy {

    public static final Duration WRITE_PERIOD = Duration.ofDays(14);

    public static final int MIN_TRUST_LEVEL = 1;

    private CompanionReviewPolicy() {}

    public static Instant deadlineOf(Instant completedAt) {
        return completedAt.plus(WRITE_PERIOD);
    }

    public static boolean isWritable(Instant completedAt, Instant now) {
        return !now.isAfter(deadlineOf(completedAt));
    }

    public static Instant revealCutoff(Instant now) {
        return now.minus(WRITE_PERIOD);
    }

    public static boolean isRevealed(boolean reverseWritten, Instant completedAt, Instant now) {
        return reverseWritten || !isWritable(completedAt, now);
    }
}
