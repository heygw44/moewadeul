package com.moewadeul.common.architecture.fixture.compliant.common.time;

import java.time.Clock;

public class ClockConfig {

    Clock clock() {
        return Clock.systemUTC();
    }
}
