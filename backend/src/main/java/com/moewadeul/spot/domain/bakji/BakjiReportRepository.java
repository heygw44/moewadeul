package com.moewadeul.spot.domain.bakji;

import java.time.Instant;

public interface BakjiReportRepository {

    BakjiReport saveAndFlush(BakjiReport report);

    boolean existsBySpotIdAndReporterId(long spotId, long reporterId);

    long countBySpotId(long spotId);

    long countBySpotIdAndReviewedAtIsNull(long spotId);

    int markReviewed(long spotId, Instant now);
}
