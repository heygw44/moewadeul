package com.moewadeul.member.application.signup;

import com.moewadeul.member.domain.MemberStatus;

public record SignupResult(Long memberId, MemberStatus status) {}
