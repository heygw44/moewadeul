package com.moewadeul.admin.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.admin.api.AdminModerationReports;
import com.moewadeul.memory.application.moderation.AdminMemorySummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdminMemoryResponse(
        long memoryId,
        AdminModerationReports.Member author,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        List<Image> images,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminModerationReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminMemoryResponse from(AdminMemorySummary summary) {
        return new AdminMemoryResponse(
                summary.memoryId(),
                new AdminModerationReports.Member(summary.authorId(), summary.authorNickname()),
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
                summary.reasonCounts(),
                AdminModerationReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }

    public record Image(long imageId, String url) {}
}
