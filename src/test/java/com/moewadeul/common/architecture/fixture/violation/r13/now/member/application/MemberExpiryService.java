package com.moewadeul.common.architecture.fixture.violation.r13.now.member.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class MemberExpiryService {

    public Instant now() {
        return Instant.now();
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
