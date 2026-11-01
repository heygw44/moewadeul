package com.moewadeul.memory.domain;

import java.util.Optional;

public interface MemoryRepository {

    Memory saveAndFlush(Memory memory);

    Optional<Memory> findById(Long id);

    // InnoDB REPEATABLE READ는 첫 일반 SELECT에서 스냅샷을 잡는다. 이 잠금 읽기가 트랜잭션의 첫 쿼리여야 뒤의 조회가 앞서 커밋된 행을 본다.
    Optional<Memory> findByIdForUpdate(long id);

    void delete(Memory memory);

    void flush();
}
