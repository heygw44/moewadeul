package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.domain.Program;
import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ProgramPaymentExpiryApplier {

    private final ProgramPaymentExpiryWriter expiryWriter;
    private final ProgramSeatReleaseRecorder seatReleaseRecorder;

    @Transactional
    boolean expire(ExpiryTarget target, Instant now) {
        Optional<Program> expired = expiryWriter.expire(target, now);
        expired.ifPresent(program -> seatReleaseRecorder.record(program, now));
        return expired.isPresent();
    }
}
