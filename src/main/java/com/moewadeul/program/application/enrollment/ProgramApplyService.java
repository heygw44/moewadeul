package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.domain.ProgramException;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import com.moewadeul.program.infra.enrollment.ProgramApplicationMapper;
import com.moewadeul.program.infra.enrollment.ProgramPaymentExpiryMapper;
import com.moewadeul.program.infra.enrollment.SeatClaim;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApplyService {

    private final ProgramRepository programRepository;
    private final ProgramApplicationMapper programApplicationMapper;
    private final ProgramPaymentExpiryMapper expiryMapper;
    private final ProgramPaymentExpiryApplier expiryApplier;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    @Transactional
    public ProgramApplyResult apply(long memberId, long programId) {
        Program program = programRepository
                .findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        program.requireApplicableAt(now);
        requireTrustLevel(program, memberId);
        expireOwnOverdueApplication(programId, memberId, now);
        if (programApplicationMapper.existsActive(programId, memberId)) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        SeatClaim claim = new SeatClaim(programId, memberId, program.paymentDueAt(now), now);
        claimSeat(claim);
        log.info("program applied programId={} applicationId={} memberId={}", programId, claim.getId(), memberId);
        return new ProgramApplyResult(
                claim.getId(),
                ProgramApplicationStatus.PENDING_PAYMENT.name(),
                claim.getPaymentDueAt(),
                program.getFee());
    }

    private void requireTrustLevel(Program program, long memberId) {
        if (!program.isOvernight()) {
            return;
        }
        int trustLevel = trustSummaryService.summarize(memberId).trustLevel();
        if (!program.isTrustLevelSufficient(trustLevel)) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    private void expireOwnOverdueApplication(long programId, long memberId, Instant now) {
        ExpiryTarget overdue = expiryMapper.selectOverdueByMember(programId, memberId, now);
        if (overdue != null) {
            expiryApplier.expire(overdue, now);
        }
    }

    private void claimSeat(SeatClaim claim) {
        int inserted;
        try {
            inserted = programApplicationMapper.insertIfSeatAvailable(claim);
        } catch (DuplicateKeyException e) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        if (inserted == 0) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_SOLD_OUT);
        }
    }
}
