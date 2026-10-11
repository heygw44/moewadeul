package com.moewadeul.common.architecture.fixture.violation.r4.infra.member.api;

import com.moewadeul.common.architecture.fixture.violation.r4.infra.member.infra.MemberPersistence;

public record MemberController(MemberPersistence memberPersistence) {}
