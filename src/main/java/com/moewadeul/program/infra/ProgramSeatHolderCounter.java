package com.moewadeul.program.infra;

import java.time.Instant;

// ProgramApplicationJpaRepository에 합쳐지는 Spring Data 조각이다. 좌석 점유 규칙을 SQL 조각 한 곳에 두려고 MyBatis로 센다.
public interface ProgramSeatHolderCounter {

    long countSeatHolders(long programId, Instant now);
}
