package com.moewadeul.admin.api.spot;

import com.moewadeul.admin.api.AdminModerationReports;
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
        AdminModerationReports.Member reporter,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminModerationReports.RecentReport> recentReports,
        Instant statusChangedAt) {

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
                reporter == null ? null : new AdminModerationReports.Member(reporter.memberId(), reporter.nickname()),
                summary.reportCount(),
                summary.reasonCounts(),
                AdminModerationReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
