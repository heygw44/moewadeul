package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.member.application.LockedMember;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.trust.application.TrustAuditDetails;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionLiftReason;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SanctionLiftApplier {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final SuspensionResyncer suspensionResyncer;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    SanctionLiftResult apply(long memberId, long sanctionId, SanctionLiftReason reason, long adminId) {
        LockedMember member = memberSuspensionService.lockForSanction(memberId);
        Sanction sanction = sanctionRepository
                .findByIdForUpdate(sanctionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        SanctionStatus before = sanction.getStatus();
        sanction.lift(adminId, reason, clock.instant());
        if (sanction.getType().isSuspension()) {
            suspensionResyncer.resync(member);
        }
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SANCTION_LIFT,
                AdminAuditTargetType.SANCTION,
                sanctionId,
                new TrustAuditDetails.Lift(
                        memberId,
                        sanction.getType().name(),
                        sanction.getLevel() == null ? null : sanction.getLevel().intValue(),
                        before.name(),
                        sanction.getStatus().name()));
        log.info("sanction lifted sanctionId={} memberId={} adminId={}", sanctionId, memberId, adminId);
        return new SanctionLiftResult(sanctionId, sanction.getStatus().name());
    }
}
