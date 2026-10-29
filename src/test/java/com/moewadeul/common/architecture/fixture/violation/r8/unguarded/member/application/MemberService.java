package com.moewadeul.common.architecture.fixture.violation.r8.unguarded.member.application;

public class MemberService {

    private final MemberApplier applier;

    public MemberService(MemberApplier applier) {
        this.applier = applier;
    }

    public void register() {
        applier.apply();
    }
}
