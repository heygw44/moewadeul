package com.moewadeul.memory.domain;

import com.moewadeul.common.moderation.ModerationStatus;

public enum MemoryStatus implements ModerationStatus {
    ACTIVE,
    PENDING_REVIEW,
    HIDDEN;

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
