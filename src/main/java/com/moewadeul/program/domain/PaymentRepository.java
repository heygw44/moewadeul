package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment saveAndFlush(Payment payment);

    // 행사 행을 기다리는 동안 커밋된 결제도 보도록 잠금 읽기로 읽는다.
    Optional<Payment> findByProgramApplicationIdForUpdate(long programApplicationId);

    List<Long> findPaidApplicationIdsByProgram(long programId);

    int refundPaidByProgram(long programId, Instant now);
}
