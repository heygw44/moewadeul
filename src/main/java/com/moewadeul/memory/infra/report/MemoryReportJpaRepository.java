package com.moewadeul.memory.infra.report;

import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface MemoryReportJpaRepository extends JpaRepository<MemoryReport, Long>, MemoryReportRepository {

    @Override
    MemoryReport saveAndFlush(MemoryReport report);

    // flushAutomatically만 켠다. clearAutomatically를 켜면 아직 플러시하지 않은 기록의 상태 변경까지 영속성 컨텍스트에서 버린다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MemoryReport r SET r.reviewedAt = :now WHERE r.memoryId = :memoryId AND r.reviewedAt IS NULL")
    int markReviewed(@Param("memoryId") long memoryId, @Param("now") Instant now);
}
