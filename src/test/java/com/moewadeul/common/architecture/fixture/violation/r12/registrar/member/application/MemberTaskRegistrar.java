package com.moewadeul.common.architecture.fixture.violation.r12.registrar.member.application;

import org.springframework.scheduling.config.ScheduledTaskRegistrar;

public class MemberTaskRegistrar {

    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(() -> {}, 1000);
    }
}
