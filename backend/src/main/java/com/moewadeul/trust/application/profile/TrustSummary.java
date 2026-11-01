package com.moewadeul.trust.application.profile;

import com.moewadeul.common.profile.TrustLevel;

public record TrustSummary(boolean identityVerified, TrustLevel trustLevel) {}
