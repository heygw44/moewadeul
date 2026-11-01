package com.moewadeul.trust.domain.profile;

import com.moewadeul.common.profile.TrustLevel;

public final class TrustLevelPolicy {

    private TrustLevelPolicy() {}

    public static TrustLevel judge(boolean identityVerified, boolean adult, CompanionRecord record) {
        if (!identityVerified || !adult) {
            return TrustLevel.EMAIL_VERIFIED;
        }
        if (record.meetsTrustedCondition()) {
            return TrustLevel.TRUSTED;
        }
        return TrustLevel.IDENTITY_VERIFIED;
    }
}
