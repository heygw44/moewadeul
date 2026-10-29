package com.moewadeul.common.architecture.fixture.compliant.member.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import org.springframework.scheduling.annotation.Scheduled;

public class MemberCleanupScheduler {

    private final ScheduledJobRunner jobRunner;

    public MemberCleanupScheduler(ScheduledJobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelay = 1000)
    public void cleanUp() {
        jobRunner.run("member-cleanup", () -> {});
    }
}
