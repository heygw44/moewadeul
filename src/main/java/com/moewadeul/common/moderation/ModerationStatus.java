package com.moewadeul.common.moderation;

public interface ModerationStatus {

    boolean isActive();

    boolean isPendingReview();

    boolean isHidden();

    String name();
}
