package com.moewadeul.common.architecture.fixture.compliant.member.infra;

import com.moewadeul.common.architecture.fixture.compliant.member.domain.Member;
import com.moewadeul.common.architecture.fixture.compliant.member.domain.MemberRepository;

public record MemberPersistence(Member member) implements MemberRepository {}
