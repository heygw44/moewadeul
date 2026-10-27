package com.moewadeul.trust.domain.profile;

public final class TrustLevelPolicy {

    private TrustLevelPolicy() {}

    public static int judge(boolean identityVerified, boolean adult, CompanionRecord record) {
        if (!identityVerified || !adult) {
            return 0;
        }
        if (record.meetsTrustedCondition()) {
            return 2;
        }
        return 1;
    }
}
