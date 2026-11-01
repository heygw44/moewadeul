package com.moewadeul.common.architecture.fixture.violation.r13.now.member.application;

import java.time.Clock;

public class MemberClockFactory {

    public Clock create() {
        return Clock.systemUTC();
    }
}
