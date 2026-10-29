package com.moewadeul.spot.domain;

import com.moewadeul.common.moderation.ModerationStatus;

public enum SpotStatus implements ModerationStatus {
    ACTIVE,
    PENDING_REVIEW,
    HIDDEN,
    DELETED;

    @Override
    public boolean isActive() {
        return this == ACTIVE;
    }

    @Override
    public boolean isPendingReview() {
        return this == PENDING_REVIEW;
    }

    @Override
    public boolean isHidden() {
        return this == HIDDEN;
    }
}
