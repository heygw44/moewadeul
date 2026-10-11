package com.moewadeul.member.application;

import com.moewadeul.member.domain.MemberStatus;

public record SignupResult(Long memberId, MemberStatus status) {}
