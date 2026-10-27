package com.moewadeul.member.domain.password;

import java.time.Duration;

public final class PasswordResetRequestPolicy {

    public static final Rule EMAIL = new Rule(Duration.ofSeconds(60), Duration.ofHours(24), 5);

    public static final Rule IP = new Rule(Duration.ZERO, Duration.ofHours(1), 20);

    public static final Duration STALE_AFTER = Duration.ofHours(24);

    private PasswordResetRequestPolicy() {}

    public static String emailKey(String normalizedEmail) {
        return ResetToken.hash("email:" + normalizedEmail);
    }

    public static String ipKey(String ip) {
        return ResetToken.hash("ip:" + ip);
    }

    public record Rule(Duration minInterval, Duration window, int maxRequests) {}
}
