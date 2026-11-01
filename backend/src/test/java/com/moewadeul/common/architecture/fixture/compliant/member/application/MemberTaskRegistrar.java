package com.moewadeul.common.architecture.fixture.compliant.member.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

public class MemberTaskRegistrar {

    private final ScheduledJobRunner jobRunner;

    public MemberTaskRegistrar(ScheduledJobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(() -> jobRunner.run("member-cleanup", () -> {}), 1000);
    }
}
