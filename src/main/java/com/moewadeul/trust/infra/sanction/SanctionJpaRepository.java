package com.moewadeul.trust.infra.sanction;

import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SanctionJpaRepository extends JpaRepository<Sanction, Long>, SanctionRepository {

    @Override
    Sanction save(Sanction sanction);

    @Override
    @Query("""
            select max(s.level) from Sanction s
            where s.memberId = :memberId
              and s.type <> com.moewadeul.trust.domain.sanction.SanctionType.TEMPORARY_72H
              and s.status <> com.moewadeul.trust.domain.sanction.SanctionStatus.LIFTED
            """)
    Optional<Byte> findHighestConfirmedLevel(@Param("memberId") long memberId);

    @Override
    @Query("""
            select count(s) > 0 from Sanction s
            where s.memberId = :memberId
              and s.status = com.moewadeul.trust.domain.sanction.SanctionStatus.ACTIVE
              and s.type <> com.moewadeul.trust.domain.sanction.SanctionType.WARNING
              and (s.endsAt is null or s.endsAt > :now)
            """)
    boolean existsActiveSuspension(@Param("memberId") long memberId, @Param("now") Instant now);

    @Override
    boolean existsByMemberIdAndStatusNot(long memberId, SanctionStatus status);

    @Override
    Optional<Sanction> findById(Long id);

    @Override
    @Query("SELECT s.memberId FROM Sanction s WHERE s.id = :sanctionId")
    Optional<Long> findMemberIdById(@Param("sanctionId") long sanctionId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sanction s WHERE s.id = :id")
    Optional<Sanction> findByIdForUpdate(@Param("id") long id);

    @Override
    @Query("""
            select s from Sanction s
            where s.memberId = :memberId
              and s.status = com.moewadeul.trust.domain.sanction.SanctionStatus.ACTIVE
              and s.type <> com.moewadeul.trust.domain.sanction.SanctionType.WARNING
            """)
    List<Sanction> findActiveSuspensions(@Param("memberId") long memberId);

    @Override
    @Query("""
            select s from Sanction s
            where s.reportId = :reportId
              and s.type = com.moewadeul.trust.domain.sanction.SanctionType.TEMPORARY_72H
              and s.status <> com.moewadeul.trust.domain.sanction.SanctionStatus.LIFTED
            """)
    List<Sanction> findUnliftedTemporaryByReportId(@Param("reportId") long reportId);
}
