package com.moewadeul.member.api.verification;

import com.moewadeul.member.application.verification.VerificationResult;
import com.moewadeul.member.domain.MemberStatus;

public record EmailVerificationResponse(MemberStatus status) {

    public static EmailVerificationResponse from(VerificationResult result) {
        return new EmailVerificationResponse(result.status());
    }
}
