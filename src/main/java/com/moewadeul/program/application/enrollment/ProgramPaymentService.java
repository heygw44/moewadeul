package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.application.ProgramApplicationEvents;
import com.moewadeul.program.domain.Payment;
import com.moewadeul.program.domain.PaymentRepository;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.domain.ProgramException;
import com.moewadeul.program.domain.ProgramRepository;
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
public class ProgramPaymentService {

    private final ProgramApplicationRepository programApplicationRepository;
    private final ProgramRepository programRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;
    private final Clock clock;

    @Transactional
    public ProgramPayResult pay(long memberId, long applicationId) {
        ProgramApplication application = lockOwnApplication(memberId, applicationId);
        Instant now = clock.instant();
        application.confirmPayment(now);
        Program program = findProgram(application.getProgramId());
        savePayment(Payment.paid(applicationId, program.getFee(), now));
        outboxEventRecorder.record(
                ProgramApplicationEvents.CONFIRMED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                applicationId,
                new ProgramApplicationEvents.ConfirmedPayload(applicationId, memberId, application.getProgramId()));
        log.info("program payment confirmed applicationId={} memberId={}", applicationId, memberId);
        return new ProgramPayResult(applicationId, ProgramApplicationStatus.CONFIRMED.name(), now);
    }

    @Transactional
    public ProgramApplicationCancelResult cancel(long memberId, long applicationId) {
        ProgramApplication application = lockOwnApplication(memberId, applicationId);
        Instant now = clock.instant();
        Program program = findProgram(application.getProgramId());
        boolean refunded = application.cancelByMember(now, program.refundDeadline());
        if (refunded) {
            refundPayment(applicationId, now);
        }
        outboxEventRecorder.record(
                ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                ProgramApplicationEvents.AGGREGATE_TYPE,
                applicationId,
                new ProgramApplicationEvents.CanceledPayload(
                        applicationId,
                        memberId,
                        application.getProgramId(),
                        ProgramCancelReason.USER.name(),
                        refunded));
        seatReleaseRecorder.record(program, now);
        log.info(
                "program application canceled applicationId={} memberId={} refunded={}",
                applicationId,
                memberId,
                refunded);
        return new ProgramApplicationCancelResult(ProgramApplicationStatus.CANCELED.name(), refunded);
    }

    private ProgramApplication lockOwnApplication(long memberId, long applicationId) {
        ProgramApplication application = programApplicationRepository
                .findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (application.getMemberId() != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return application;
    }

    private Program findProgram(long programId) {
        return programRepository
                .findById(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void savePayment(Payment payment) {
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
    }

    private void refundPayment(long applicationId, Instant now) {
        Payment payment = paymentRepository
                .findByProgramApplicationId(applicationId)
                .orElseThrow(() -> new IllegalStateException("확정된 신청에 결제가 없습니다. applicationId=" + applicationId));
        payment.refund(now);
    }
}
