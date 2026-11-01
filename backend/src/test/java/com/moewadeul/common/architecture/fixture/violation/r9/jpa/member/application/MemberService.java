package com.moewadeul.common.architecture.fixture.violation.r9.jpa.member.application;

import com.moewadeul.common.architecture.fixture.violation.r9.jpa.member.infra.MemberJpaRepository;

public record MemberService(MemberJpaRepository memberJpaRepository) {}
