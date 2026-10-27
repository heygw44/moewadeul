package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.AdminCommunityCommentSummary;
import java.time.Instant;
import java.util.List;

public record AdminCommunityCommentResponse(
        long commentId,
        long postId,
        String content,
        AdminCommunityReports.Author author,
        String status,
        long reportCount,
        AdminCommunityReports.ReasonCounts reasonCounts,
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
                AdminCommunityReports.ReasonCounts.from(summary.reasonCounts()),
                AdminCommunityReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
