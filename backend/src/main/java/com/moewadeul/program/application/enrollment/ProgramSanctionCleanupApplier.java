package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.application.enrollment.ProgramApplicationLocker.LockedApplication;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ProgramSanctionCleanupApplier {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramApplicationLocker applicationLocker;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    boolean cancelPending(long applicationId, long memberId, ProgramCancelReason reason) {
        LockedApplication locked = applicationLocker.lock(applicationId).orElse(null);
        if (locked == null || locked.application().getStatus() != ProgramApplicationStatus.PENDING_PAYMENT) {
            return false;
        }
        ProgramApplication application = locked.application();
        Instant now = clock.instant();
        application.cancelByMemberRemoval(reason, now);
        outboxEventRecorder.recordAll(application.pullEvents());
        seatReleaseRecorder.record(locked.program(), now);
        log.info(
                "program application canceled reason={} applicationId={} memberId={}", reason, applicationId, memberId);
        return true;
    }

    @Transactional
    int requestReview(long memberId) {
        return applicationRepository.requestReviewForConfirmed(memberId, clock.instant());
    }
}
