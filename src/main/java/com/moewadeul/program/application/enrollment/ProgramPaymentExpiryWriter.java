package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import com.moewadeul.program.infra.enrollment.ProgramPaymentExpiryMapper;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ProgramPaymentExpiryWriter {

    private final ProgramPaymentExpiryMapper expiryMapper;
    private final OutboxEventRecorder outboxEventRecorder;
    private final ProgramRepository programRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Program> expire(ExpiryTarget target, Instant now) {
        Program program =
                programRepository.findByIdForUpdate(target.programId()).orElseThrow(BusinessException::notFound);
        if (expiryMapper.expireIfDue(target.applicationId(), now) == 0) {
            return Optional.empty();
        }
        outboxEventRecorder.record(
                ProgramEvents.APPLICATION_EXPIRED,
                ProgramEvents.APPLICATION_AGGREGATE_TYPE,
                target.applicationId(),
                new ProgramEvents.ApplicationPayload(target.applicationId(), target.memberId(), target.programId()));
        log.info("program application expired applicationId={} memberId={}", target.applicationId(), target.memberId());
        return Optional.of(program);
    }
}
