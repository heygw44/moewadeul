package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ProgramPaymentExpiryApplier {

    private final ProgramPaymentExpiryWriter expiryWriter;

    @Transactional
    boolean expire(ExpiryTarget target, Instant now) {
        return expiryWriter.expire(target, now);
    }
}
