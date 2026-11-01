package com.moewadeul.spot.infra;

import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotJpaRepository extends JpaRepository<Spot, Long>, SpotRepository {

    @Override
    Spot save(Spot spot);

    @Override
    Optional<Spot> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Spot s WHERE s.id = :id")
    Optional<Spot> findByIdForUpdate(@Param("id") long id);

    // 커밋 직전에 Hibernate가 version을 조건으로 건 UPDATE로 번호를 올린다. UPDATE는 커밋된 최신 행을 보므로, REPEATABLE READ에서도
    // 읽은 뒤 커밋된 다른 쓰기를 알아차린다.
    @Override
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT s FROM Spot s WHERE s.id = :id")
    Optional<Spot> findByIdForVersionIncrement(@Param("id") long id);
}
