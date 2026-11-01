package com.moewadeul.basecamp.infra;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.BasecampStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BasecampJpaRepository extends JpaRepository<Basecamp, Long>, BasecampRepository {

    @Override
    Basecamp saveAndFlush(Basecamp basecamp);

    @Override
    Optional<Basecamp> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Basecamp b where b.id = :id")
    Optional<Basecamp> findByIdForUpdate(@Param("id") Long id);

    @Override
    void flush();

    @Override
    long countByLeaderIdAndStatusIn(long leaderId, Collection<BasecampStatus> statuses);
}
