package com.moewadeul.memory.domain;

import java.util.Optional;

public interface MemoryRepository {

    Memory saveAndFlush(Memory memory);

    Optional<Memory> findById(Long id);

    /** 호출하면 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 트랜잭션 안에서만 부른다. */
    Optional<Memory> findByIdForUpdate(long id);

    void delete(Memory memory);

    void flush();
}
