package com.moewadeul.trust.application.identity;

import com.moewadeul.common.profile.TrustLevel;

public record IdentityVerificationResult(boolean identityVerified, boolean adult, TrustLevel trustLevel) {}
