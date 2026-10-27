package com.moewadeul.program.infra;

import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramJpaRepository extends JpaRepository<Program, Long>, ProgramRepository {

    @Override
    Program saveAndFlush(Program program);

    @Override
    Optional<Program> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Program p where p.id = :id")
    Optional<Program> findByIdForUpdate(@Param("id") Long id);
}
