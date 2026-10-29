package com.moewadeul.common.retry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record RetryPolicy(Duration lease, Duration retryDelay, Duration retryMaxDelay, int maxAttempts) {

    public static void requireValid(
            String prefix, Duration lease, Duration retryDelay, Duration retryMaxDelay, int maxAttempts) {
        requirePositive(prefix, lease, "lease");
        requirePositive(prefix, retryDelay, "retryDelay");
        requirePositive(prefix, retryMaxDelay, "retryMaxDelay");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(prefix + ".maxAttempts must be positive");
        }
        if (retryMaxDelay.compareTo(retryDelay) < 0) {
            throw new IllegalArgumentException(prefix + ".retryMaxDelay must not be shorter than retryDelay");
        }
    }

    public Instant leaseUntil(Instant now) {
        // 컬럼이 마이크로초까지만 저장하므로, 나노초가 붙은 시각은 저장한 값과 같은지 비교할 때 어긋날 수 있다.
        return now.plus(lease).truncatedTo(ChronoUnit.MICROS);
    }

    public long retryDelayMillis() {
        return retryDelay.toMillis();
    }

    public long retryMaxDelayMillis() {
        return retryMaxDelay.toMillis();
    }

    private static void requirePositive(String prefix, Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(prefix + "." + name + " must be positive");
        }
    }
}
