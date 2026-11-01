package com.moewadeul.community.infra.report;

import com.moewadeul.community.domain.report.CommunityReport;
import com.moewadeul.community.domain.report.CommunityReportRepository;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityReportJpaRepository extends JpaRepository<CommunityReport, Long>, CommunityReportRepository {

    @Override
    CommunityReport saveAndFlush(CommunityReport report);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CommunityReport r SET r.reviewedAt = :now "
            + "WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.reviewedAt IS NULL")
    int markReviewed(
            @Param("targetType") CommunityReportTargetType targetType,
            @Param("targetId") long targetId,
            @Param("now") Instant now);
}
