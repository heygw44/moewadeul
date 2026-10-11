package com.moewadeul.trust.api;

import com.moewadeul.trust.application.IdentityVerificationResult;

public record IdentityVerificationResponse(boolean identityVerified, boolean adult, int trustLevel) {

    static IdentityVerificationResponse from(IdentityVerificationResult result) {
        return new IdentityVerificationResponse(result.identityVerified(), result.adult(), result.trustLevel());
    }
}
