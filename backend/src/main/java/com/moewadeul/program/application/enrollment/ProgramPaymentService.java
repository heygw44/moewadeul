package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.transaction.ReadCommittedTransaction;
import com.moewadeul.program.application.enrollment.ProgramApplicationLocker.LockedApplication;
import com.moewadeul.program.domain.Payment;
import com.moewadeul.program.domain.PaymentRepository;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPaymentService {

    private static final String PAYMENT_UNIQUE_CONSTRAINT = "uk_payment_program_application";

    private final ProgramApplicationLocker applicationLocker;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final Clock clock;

    @Transactional
    public ProgramPayResult pay(long memberId, long applicationId) {
        LockedApplication locked = lockOwnApplication(memberId, applicationId);
        ProgramApplication application = locked.application();
        Instant now = clock.instant();
        application.confirmPayment(now);
        Program program = locked.program();
        savePayment(Payment.paid(applicationId, program.getFee(), now));
        outboxEventRecorder.recordAll(application.pullEvents());
        log.info("program payment confirmed applicationId={} memberId={}", applicationId, memberId);
        return new ProgramPayResult(applicationId, ProgramApplicationStatus.CONFIRMED, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProgramApplicationCancelResult cancel(long memberId, long applicationId) {
        ReadCommittedTransaction.require("행사 행을 잠근 뒤 자리를 다시 세는 읽기가 트랜잭션 처음의 낡은 스냅숏이 아니라 최신 커밋을 봐야 한다.");
        LockedApplication locked = lockOwnApplication(memberId, applicationId);
        ProgramApplication application = locked.application();
        Instant now = clock.instant();
        Program program = locked.program();
        boolean refunded = application.cancelByMember(now, program.refundDeadline());
        if (refunded) {
            refundPayment(applicationId, now);
        }
        outboxEventRecorder.recordAll(application.pullEvents());
        seatReleaseRecorder.record(program, now);
        log.info(
                "program application canceled applicationId={} memberId={} refunded={}",
                applicationId,
                memberId,
                refunded);
        return new ProgramApplicationCancelResult(ProgramApplicationStatus.CANCELED, refunded);
    }

    private LockedApplication lockOwnApplication(long memberId, long applicationId) {
        LockedApplication locked = applicationLocker.lock(applicationId).orElseThrow(BusinessException::notFound);
        locked.application().requireAppliedBy(memberId);
        return locked;
    }

    private void savePayment(Payment payment) {
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolations.translate(e, PAYMENT_UNIQUE_CONSTRAINT, ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
    }

    private void refundPayment(long applicationId, Instant now) {
        Payment payment = paymentRepository
                .findByProgramApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> new IllegalStateException("확정된 신청에 결제가 없습니다. applicationId=" + applicationId));
        payment.refund(now);
    }
}
