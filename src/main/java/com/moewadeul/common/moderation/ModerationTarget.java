package com.moewadeul.common.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import java.time.Instant;

public interface ModerationTarget<T extends Moderatable> {

    AdminAuditAction hideAction();

    AdminAuditAction restoreAction();

    String auditTargetType();

    T lock(long targetId);

    // 숨김만 막는 대상은 여기서 거른다. 이미 숨겨진 비공개 기록의 복구는 막지 않는다.
    default boolean isHideTarget(T target) {
        return true;
    }

    int markReviewed(long targetId, Instant now);

    default void afterHide(T target, Instant now) {}

    default void afterRestore(T target, Instant now) {}

    default Object auditDetail(T target, Object transition) {
        return transition;
    }
}
