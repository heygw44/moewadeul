package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApplyService {

    private static final String ACTIVE_MEMBER_UNIQUE_CONSTRAINT = "uk_program_application_active_member";

    private final ProgramRepository programRepository;
    private final ProgramApplicationMapper programApplicationMapper;
    private final ProgramPaymentExpiryMapper expiryMapper;
    private final ProgramPaymentExpiryWriter expiryWriter;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    // 매진 뒤 몰린 요청이 행사 행 잠금 줄에 서면 그 줄이 Tomcat 스레드를 붙잡아 지도까지 멈춘다. 확실히 매진일 때만 잠금 없이 거절한다.
    public void rejectIfSoldOutWithoutLock(long memberId, long programId) {
        Program program = programRepository.findById(programId).orElseThrow(BusinessException::notFound);
        Instant now = clock.instant();
        if (programApplicationMapper.countSeatHolders(programId, now) < program.getCapacity()) {
            return;
        }
        program.requireApplicableAt(now);
        requireTrustLevel(program, memberId);
        if (programApplicationMapper.existsActive(programId, memberId)) {
            return;
        }
        throw new BusinessException(ProgramErrorCode.PROGRAM_SOLD_OUT);
    }

    @Transactional
    public ProgramApplyResult apply(long memberId, long programId) {
        Program program = programRepository.findByIdForUpdate(programId).orElseThrow(BusinessException::notFound);
        Instant now = clock.instant();
        program.requireApplicableAt(now);
        requireTrustLevel(program, memberId);
        expireOwnOverdueApplication(programId, memberId, now);
        if (programApplicationMapper.existsActive(programId, memberId)) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        SeatClaim claim = new SeatClaim(programId, memberId, program.paymentDueAt(now), now);
        claimSeat(claim);
        log.info("program applied programId={} applicationId={} memberId={}", programId, claim.getId(), memberId);
        return new ProgramApplyResult(
                claim.getId(), ProgramApplicationStatus.PENDING_PAYMENT, claim.getPaymentDueAt(), program.getFee());
    }

    private void requireTrustLevel(Program program, long memberId) {
        if (!program.isOvernight()) {
            return;
        }
        trustSummaryService.requireLevel(memberId, Program.MIN_TRUST_LEVEL_FOR_OVERNIGHT);
    }

    private void expireOwnOverdueApplication(long programId, long memberId, Instant now) {
        ExpiryTarget overdue = expiryMapper.selectOverdueByMember(programId, memberId, now);
        if (overdue != null) {
            expiryWriter.expire(overdue, now);
        }
    }

    private void claimSeat(SeatClaim claim) {
        int inserted;
        try {
            inserted = programApplicationMapper.insertIfSeatAvailable(claim);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolations.translate(
                    e, ACTIVE_MEMBER_UNIQUE_CONSTRAINT, ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        if (inserted == 0) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_SOLD_OUT);
        }
    }
}
