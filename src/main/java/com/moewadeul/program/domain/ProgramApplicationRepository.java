package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository {

    Optional<ProgramApplication> findByIdForUpdate(Long id);

    Optional<Long> findProgramId(long id);

    List<ProgramApplication> findActiveByProgramForUpdate(long programId);

    int cancelActiveByProgram(long programId, ProgramCancelReason reason, Instant now);

    List<Long> findPendingPaymentIdsByMember(long memberId);

    int capPendingPaymentDueAt(long programId, Instant startAt, Instant now);

    int requestReviewForConfirmed(long memberId, Instant now);

    boolean existsByProgramId(long programId);

    long countSeatHolders(long programId, Instant now);
}
