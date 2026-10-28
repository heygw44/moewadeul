package com.moewadeul.member.domain.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public final class LoginLockout {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private LoginLockout() {}

    public static Instant windowStart(Instant now) {
        return now.minus(WINDOW);
    }

    public static String attemptKey(String attemptedEmail) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(attemptedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    public static Optional<Instant> lockedUntil(List<Instant> failureTimesSinceLastSuccess, Instant now) {
        Instant windowStart = windowStart(now);
        List<Instant> inWindow = failureTimesSinceLastSuccess.stream()
                .filter(failedAt -> failedAt.isAfter(windowStart))
                .sorted()
                .toList();
        if (inWindow.size() < MAX_FAILURES) {
            return Optional.empty();
        }
        Instant oldestOfLatestFailures = inWindow.get(inWindow.size() - MAX_FAILURES);
        return Optional.of(oldestOfLatestFailures.plus(WINDOW));
    }
}
