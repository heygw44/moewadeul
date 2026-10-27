package com.moewadeul.trust.infra.report;

import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.MemberReportRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberReportJpaRepository extends JpaRepository<MemberReport, Long>, MemberReportRepository {

    @Override
    MemberReport saveAndFlush(MemberReport report);

    @Override
    Optional<MemberReport> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MemberReport r WHERE r.id = :id")
    Optional<MemberReport> findByIdForUpdate(@Param("id") long id);
}
