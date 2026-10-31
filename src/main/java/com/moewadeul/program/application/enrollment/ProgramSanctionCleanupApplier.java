package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.transaction.ReadCommittedTransaction;
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
        ReadCommittedTransaction.require("행사 행을 잠근 뒤 자리를 다시 세는 읽기가 트랜잭션 처음의 낡은 스냅숏이 아니라 최신 커밋을 봐야 한다.");
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
