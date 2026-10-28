package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment saveAndFlush(Payment payment);

    Optional<Payment> findByProgramApplicationId(long programApplicationId);

    List<Long> findPaidApplicationIdsByProgram(long programId);

    int refundPaidByProgram(long programId, Instant now);
}
