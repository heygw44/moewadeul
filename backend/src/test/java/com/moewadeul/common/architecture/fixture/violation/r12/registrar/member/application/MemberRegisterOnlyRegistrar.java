package com.moewadeul.common.architecture.fixture.violation.r12.registrar.member.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

public class MemberRegisterOnlyRegistrar {

    private final ScheduledJobRunner jobRunner;

    public MemberRegisterOnlyRegistrar(ScheduledJobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    public void configureTasks(ScheduledTaskRegistrar registrar) {
        jobRunner.register("member-cleanup");
        registrar.addCronTask(() -> {}, "0 0 5 * * *");
    }
}
