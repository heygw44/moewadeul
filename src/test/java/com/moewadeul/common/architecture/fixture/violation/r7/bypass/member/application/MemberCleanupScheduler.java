package com.moewadeul.common.architecture.fixture.violation.r7.bypass.member.application;

import org.springframework.scheduling.annotation.Scheduled;

public class MemberCleanupScheduler {

    @Scheduled(fixedDelay = 1000)
    public void cleanUp() {}
}
