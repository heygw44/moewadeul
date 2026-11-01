package com.moewadeul.common.architecture.fixture.violation.r8.caller.member.application;

public class MemberCleanupJob {

    private final MemberApplier applier;

    public MemberCleanupJob(MemberApplier applier) {
        this.applier = applier;
    }

    public void cleanUp() {
        applier.apply();
    }
}
