package com.moewadeul.trust.domain;

import com.moewadeul.common.profile.TrustLevel;
import java.time.Duration;
import java.time.Instant;

public final class CompanionReviewPolicy {

    public static final Duration WRITE_PERIOD = Duration.ofDays(14);

    public static final TrustLevel MIN_TRUST_LEVEL = TrustLevel.IDENTITY_VERIFIED;

    private CompanionReviewPolicy() {}

    public static Instant deadlineOf(Instant completedAt) {
        return completedAt.plus(WRITE_PERIOD);
    }

    public static boolean isWritable(Instant completedAt, Instant now) {
        return !now.isAfter(deadlineOf(completedAt));
    }

    public static boolean isEligible(
            boolean basecampCompleted,
            Instant completedAt,
            boolean reviewerActive,
            boolean revieweeActive,
            long reviewerId,
            long revieweeId) {
        return basecampCompleted && completedAt != null && reviewerActive && revieweeActive && reviewerId != revieweeId;
    }

    public static Instant revealCutoff(Instant now) {
        return now.minus(WRITE_PERIOD);
    }

    public static boolean isRevealed(boolean reverseWritten, Instant completedAt, Instant now) {
        return reverseWritten || !isWritable(completedAt, now);
    }
}
