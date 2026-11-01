package com.moewadeul.spot.infra.bakji;

import com.moewadeul.spot.domain.bakji.BakjiReport;
import com.moewadeul.spot.domain.bakji.BakjiReportRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BakjiReportJpaRepository extends JpaRepository<BakjiReport, Long>, BakjiReportRepository {

    @Override
    BakjiReport saveAndFlush(BakjiReport report);

    // flushAutomatically만 켠다. clearAutomatically를 켜면 아직 플러시하지 않은 장소 상태 변경까지 영속성 컨텍스트에서 버린다.
    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE BakjiReport r SET r.reviewedAt = :now WHERE r.spotId = :spotId AND r.reviewedAt IS NULL")
    int markReviewed(@Param("spotId") long spotId, @Param("now") Instant now);
}
