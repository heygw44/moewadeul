package com.moewadeul.common.architecture.fixture.violation.r7.bypass.member.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import org.springframework.scheduling.annotation.Scheduled;

public class MemberRegisterOnlyScheduler {

    private final ScheduledJobRunner jobRunner;

    public MemberRegisterOnlyScheduler(ScheduledJobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelay = 1000)
    public void cleanUp() {
        jobRunner.register("member-cleanup");
    }
}
