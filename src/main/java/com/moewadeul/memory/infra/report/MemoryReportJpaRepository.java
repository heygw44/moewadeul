package com.moewadeul.memory.infra.report;

import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryReportJpaRepository extends JpaRepository<MemoryReport, Long>, MemoryReportRepository {

    @Override
    MemoryReport saveAndFlush(MemoryReport report);

    // flushAutomatically만 켠다. clearAutomatically를 켜면 아직 플러시하지 않은 기록의 상태 변경까지 영속성 컨텍스트에서 버린다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MemoryReport r SET r.reviewedAt = :now WHERE r.memoryId = :memoryId AND r.reviewedAt IS NULL")
    int markReviewed(@Param("memoryId") long memoryId, @Param("now") Instant now);
}
