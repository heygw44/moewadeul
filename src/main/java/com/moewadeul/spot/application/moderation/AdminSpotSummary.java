package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.moderation.RecentReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminSpotSummary(
        long spotId,
        String type,
        String name,
        String status,
        double lat,
        double lng,
        boolean parkWarning,
        Reporter reporter,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    public record Reporter(long memberId, String nickname) {}
}
