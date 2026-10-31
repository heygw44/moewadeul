package com.moewadeul.common.architecture.fixture.compliant.member.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

public class MemberExpiryService {

    private final Clock clock;

    public MemberExpiryService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
