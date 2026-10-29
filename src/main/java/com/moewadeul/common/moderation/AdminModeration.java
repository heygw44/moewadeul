package com.moewadeul.common.moderation;

import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminModeration {

    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    public <T extends Moderatable> ModerationResult hide(ModerationTarget<T> target, long targetId, long adminId) {
        T entity = target.lock(targetId);
        if (!target.canBeHidden(entity)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        ModerationStatus before = entity.getStatus();
        Instant now = clock.instant();
        entity.hide(now);
        target.afterHide(entity, now);
        ModerationStatus after = entity.getStatus();
        adminAuditRecorder.record(
                adminId,
                target.hideAction(),
                target.auditTargetType(),
                targetId,
                target.auditDetail(entity, new ModerationAuditDetails.Hide(before.name(), after.name())));
        log.info("{} hidden targetId={} adminId={}", target.auditTargetType(), targetId, adminId);
        return new ModerationResult(targetId, after.name());
    }

    public <T extends Moderatable> ModerationResult restore(ModerationTarget<T> target, long targetId, long adminId) {
        T entity = target.lock(targetId);
        ModerationStatus before = entity.getStatus();
        Instant now = clock.instant();
        entity.restore(now);
        target.afterRestore(entity, now);
        int reviewed = target.markReviewed(targetId, now);
        ModerationStatus after = entity.getStatus();
        adminAuditRecorder.record(
                adminId,
                target.restoreAction(),
                target.auditTargetType(),
                targetId,
                target.auditDetail(entity, new ModerationAuditDetails.Restore(before.name(), after.name(), reviewed)));
        log.info(
                "{} restored targetId={} adminId={} reviewedReportCount={}",
                target.auditTargetType(),
                targetId,
                adminId,
                reviewed);
        return new ModerationResult(targetId, after.name());
    }
}
