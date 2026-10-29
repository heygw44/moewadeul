package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.AdminCommunityPostSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityPostResponse(
        long postId,
        String title,
        String content,
        AdminCommunityReports.Author author,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminCommunityReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityPostResponse from(AdminCommunityPostSummary summary) {
        return new AdminCommunityPostResponse(
                summary.postId(),
                summary.title(),
                summary.content(),
                AdminCommunityReports.Author.from(summary.author()),
                summary.status(),
                summary.reportCount(),
                summary.reasonCounts(),
                AdminCommunityReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
