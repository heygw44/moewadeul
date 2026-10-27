package com.moewadeul.spot.application.moderation;

import java.time.Instant;
import java.util.List;

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
        ReasonCounts reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    public record Reporter(long memberId, String nickname) {}

    public record ReasonCounts(long illegalArea, long closed, long falseInfo) {}

    public record RecentReport(String reason, String content, Instant createdAt) {}
}
