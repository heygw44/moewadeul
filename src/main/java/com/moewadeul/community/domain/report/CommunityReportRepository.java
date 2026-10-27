package com.moewadeul.community.domain.report;

import java.time.Instant;

public interface CommunityReportRepository {

    CommunityReport saveAndFlush(CommunityReport report);

    boolean existsByTargetTypeAndTargetIdAndReporterId(
            CommunityReportTargetType targetType, long targetId, long reporterId);

    long countByTargetTypeAndTargetIdAndReviewedAtIsNull(CommunityReportTargetType targetType, long targetId);

    int markReviewed(CommunityReportTargetType targetType, long targetId, Instant now);
}
