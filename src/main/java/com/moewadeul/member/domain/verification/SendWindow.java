package com.moewadeul.member.domain.verification;

import java.time.Instant;

public record SendWindow(long count, Instant oldestSentAt) {

    public SendWindow plus(SendWindow other) {
        Instant oldest = oldestSentAt;
        if (oldest == null || (other.oldestSentAt != null && other.oldestSentAt.isBefore(oldest))) {
            oldest = other.oldestSentAt;
        }
        return new SendWindow(count + other.count, oldest);
    }
}
