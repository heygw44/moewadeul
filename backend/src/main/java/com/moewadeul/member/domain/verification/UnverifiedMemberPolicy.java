package com.moewadeul.member.domain.verification;

import java.time.Duration;
import java.time.Instant;

public final class UnverifiedMemberPolicy {

    public static final Duration UNVERIFIED_RETENTION = Duration.ofDays(7);

    private UnverifiedMemberPolicy() {}

    public static Instant deletionCutoff(Instant now) {
        return now.minus(UNVERIFIED_RETENTION);
    }
}
