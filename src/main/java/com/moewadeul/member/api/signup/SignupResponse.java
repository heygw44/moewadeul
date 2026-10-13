package com.moewadeul.member.api.signup;

import com.moewadeul.member.application.signup.SignupResult;
import com.moewadeul.member.domain.MemberStatus;

public record SignupResponse(Long memberId, MemberStatus status) {

    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(result.memberId(), result.status());
    }
}
