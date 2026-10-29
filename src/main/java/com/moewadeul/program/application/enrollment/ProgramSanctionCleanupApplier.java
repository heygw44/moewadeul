package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ProgramSanctionCleanupApplier {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    boolean cancelPending(long applicationId, long memberId, ProgramCancelReason reason) {
        ProgramApplication application =
                applicationRepository.findByIdForUpdate(applicationId).orElse(null);
        if (application == null || application.getStatus() != ProgramApplicationStatus.PENDING_PAYMENT) {
            return false;
        }
        Instant now = clock.instant();
        application.cancelByMemberRemoval(reason, now);
        outboxEventRecorder.recordAll(application.pullEvents());
        Program program = programRepository
                .findById(application.getProgramId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        seatReleaseRecorder.record(program, now);
        log.info(
                "program application canceled reason={} applicationId={} memberId={}", reason, applicationId, memberId);
        return true;
    }

    @Transactional
    int requestReview(long memberId) {
        return applicationRepository.requestReviewForConfirmed(memberId, clock.instant());
    }
}
