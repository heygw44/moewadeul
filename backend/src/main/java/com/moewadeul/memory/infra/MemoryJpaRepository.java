package com.moewadeul.memory.infra;

import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryJpaRepository extends JpaRepository<Memory, Long>, MemoryRepository {

    @Override
    Memory saveAndFlush(Memory memory);

    @Override
    Optional<Memory> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memory m WHERE m.id = :id")
    Optional<Memory> findByIdForUpdate(@Param("id") long id);

    @Override
    void delete(Memory memory);

    @Override
    void flush();
}
