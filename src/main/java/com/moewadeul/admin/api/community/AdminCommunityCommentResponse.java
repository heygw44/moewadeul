package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.AdminCommunityCommentSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityCommentResponse(
        long commentId,
        long postId,
        String content,
        AdminCommunityReports.Author author,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminCommunityReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityCommentResponse from(AdminCommunityCommentSummary summary) {
        return new AdminCommunityCommentResponse(
                summary.commentId(),
                summary.postId(),
                summary.content(),
                AdminCommunityReports.Author.from(summary.author()),
                summary.status(),
                summary.reportCount(),
                summary.reasonCounts(),
                AdminCommunityReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
