package com.moewadeul.trust.application.identity;

public record IdentityVerificationResult(boolean identityVerified, boolean adult, int trustLevel) {}
