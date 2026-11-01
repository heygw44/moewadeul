package com.moewadeul.program.domain;

import java.util.Optional;

public interface ProgramRepository {

    Program saveAndFlush(Program program);

    Optional<Program> findById(Long id);

    // InnoDB REPEATABLE READ는 첫 일반 SELECT에서 스냅샷을 잡는다. 이 잠금 읽기가 트랜잭션의 첫 쿼리여야 뒤의 조회가 앞서 커밋된 행을 본다.
    Optional<Program> findByIdForUpdate(Long id);
}
