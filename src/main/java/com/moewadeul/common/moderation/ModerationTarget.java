package com.moewadeul.common.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditTargetType;
import java.time.Instant;

public interface ModerationTarget<T extends Moderatable> {

    AdminAuditAction hideAction();

    AdminAuditAction restoreAction();

    AdminAuditTargetType auditTargetType();

    T lock(long targetId);

    default boolean canBeHidden(T target) {
        return true;
    }

    int markReviewed(long targetId, Instant now);

    default void afterHide(T target, Instant now) {}

    default void afterRestore(T target, Instant now) {}

    default Object auditDetail(T target, Object transition) {
        return transition;
    }
}
