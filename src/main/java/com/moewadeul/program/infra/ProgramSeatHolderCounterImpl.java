package com.moewadeul.program.infra;

import com.moewadeul.program.infra.enrollment.ProgramApplicationMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ProgramSeatHolderCounterImpl implements ProgramSeatHolderCounter {

    private final EntityManager entityManager;
    private final ProgramApplicationMapper mapper;

    // MyBatis 집계는 Hibernate auto-flush를 받지 못하므로, 같은 트랜잭션에서 바꾼 신청 상태를 먼저 내보내야 센다.
    @Override
    public long countSeatHolders(long programId, Instant now) {
        if (entityManager.isJoinedToTransaction()) {
            entityManager.flush();
        }
        return mapper.countSeatHolders(programId, now);
    }
}
