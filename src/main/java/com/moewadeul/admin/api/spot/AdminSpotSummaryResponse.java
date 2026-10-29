package com.moewadeul.admin.api.spot;

import com.moewadeul.spot.application.moderation.AdminSpotSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminSpotSummaryResponse(
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

    public record RecentReport(String reason, String content, Instant createdAt) {}

    static AdminSpotSummaryResponse from(AdminSpotSummary summary) {
        AdminSpotSummary.Reporter reporter = summary.reporter();
        return new AdminSpotSummaryResponse(
                summary.spotId(),
                summary.type(),
                summary.name(),
                summary.status(),
                summary.lat(),
                summary.lng(),
                summary.parkWarning(),
                reporter == null ? null : new Reporter(reporter.memberId(), reporter.nickname()),
                summary.reportCount(),
                summary.reasonCounts(),
                summary.recentReports().stream()
                        .map(report -> new RecentReport(report.reason(), report.content(), report.createdAt()))
                        .toList(),
                summary.statusChangedAt());
    }
}
