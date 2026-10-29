package com.moewadeul.admin.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moewadeul.admin.api.community.AdminCommunityReports;
import com.moewadeul.memory.application.moderation.AdminMemorySummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminMemoryResponse(
        long memoryId,
        AdminCommunityReports.Author author,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        List<Image> images,
        String status,
        long reportCount,
        ReasonCounts reasonCounts,
        List<AdminCommunityReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminMemoryResponse from(AdminMemorySummary summary) {
        return new AdminMemoryResponse(
                summary.memoryId(),
                new AdminCommunityReports.Author(summary.authorId(), summary.authorNickname()),
                summary.approxLat(),
                summary.approxLng(),
                summary.visitedDate(),
                summary.nights(),
                summary.content(),
                summary.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                summary.status(),
                summary.reportCount(),
                ReasonCounts.from(summary.reasonCounts()),
                summary.recentReports().stream()
                        .map(report -> new AdminCommunityReports.RecentReport(
                                report.reason(), report.content(), report.createdAt()))
                        .toList(),
                summary.statusChangedAt());
    }

    public record Image(long imageId, String url) {}

    public record ReasonCounts(
            @JsonProperty("SPAM") long spam,
            @JsonProperty("ABUSE") long abuse,
            @JsonProperty("ILLEGAL_CAMPING") long illegalCamping,
            @JsonProperty("PRIVACY") long privacy,
            @JsonProperty("MONEY_SCAM") long moneyScam,
            @JsonProperty("OTHER") long other) {

        static ReasonCounts from(AdminMemorySummary.ReasonCounts counts) {
            return new ReasonCounts(
                    counts.spam(),
                    counts.abuse(),
                    counts.illegalCamping(),
                    counts.privacy(),
                    counts.moneyScam(),
                    counts.other());
        }
    }
}
