package com.moewadeul.memory.domain;

import java.util.Optional;

public interface MemoryRepository {

    Memory saveAndFlush(Memory memory);

    Optional<Memory> findById(Long id);

    void delete(Memory memory);

    void flush();
}
