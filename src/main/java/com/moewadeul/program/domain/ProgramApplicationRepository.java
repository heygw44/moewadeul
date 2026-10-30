package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository {

    // InnoDB REPEATABLE READ는 첫 일반 SELECT에서 스냅샷을 잡는다. 이 잠금 읽기가 트랜잭션의 첫 쿼리여야 뒤의 조회가 앞서 커밋된 행을 본다.
    Optional<ProgramApplication> findByIdForUpdate(Long id);

    List<ProgramApplication> findActiveByProgramForUpdate(long programId);

    int cancelActiveByProgram(long programId, ProgramCancelReason reason, Instant now);

    List<Long> findPendingPaymentIdsByMember(long memberId);

    int capPendingPaymentDueAt(long programId, Instant startAt, Instant now);

    int requestReviewForConfirmed(long memberId, Instant now);

    boolean existsByProgramId(long programId);

    long countSeatHolders(long programId, Instant now);
}
