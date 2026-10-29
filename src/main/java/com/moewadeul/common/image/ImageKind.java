package com.moewadeul.common.image;

public record ImageKind(String keyPrefix, ImageMessages messages, int maxPerParent) {

    private static final int UNATTACHED_LIMIT_MULTIPLIER = 3;

    public int maxUnattachedPerMember() {
        return maxPerParent * UNATTACHED_LIMIT_MULTIPLIER;
    }
}
