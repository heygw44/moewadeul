package com.moewadeul.common.architecture.fixture.violation.r8.unguarded.member.application;

import org.springframework.transaction.annotation.Transactional;

class MemberApplier {

    @Transactional
    void apply() {}
}
