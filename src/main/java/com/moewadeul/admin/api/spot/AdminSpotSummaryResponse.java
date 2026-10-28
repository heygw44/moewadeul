package com.moewadeul.admin.api.spot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moewadeul.spot.application.moderation.AdminSpotSummary;
import java.time.Instant;
import java.util.List;

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
        ReasonCounts reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    public record Reporter(long memberId, String nickname) {}

    public record ReasonCounts(
            @JsonProperty("ILLEGAL_AREA") long illegalArea,
            @JsonProperty("CLOSED") long closed,
            @JsonProperty("FALSE_INFO") long falseInfo) {}

    public record RecentReport(String reason, String content, Instant createdAt) {}

    static AdminSpotSummaryResponse from(AdminSpotSummary summary) {
        AdminSpotSummary.Reporter reporter = summary.reporter();
        AdminSpotSummary.ReasonCounts counts = summary.reasonCounts();
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
                new ReasonCounts(counts.illegalArea(), counts.closed(), counts.falseInfo()),
                summary.recentReports().stream()
                        .map(report -> new RecentReport(report.reason(), report.content(), report.createdAt()))
                        .toList(),
                summary.statusChangedAt());
    }
}
