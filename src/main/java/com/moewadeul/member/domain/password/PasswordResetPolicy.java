package com.moewadeul.member.domain.password;

import java.time.Duration;

public final class PasswordResetPolicy {

    public static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

    public static final Duration TOKEN_RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    public static final int TOKEN_BYTES = 32;

    private PasswordResetPolicy() {}
}
