package com.moewadeul.admin.api.community;

import com.moewadeul.admin.api.AdminModerationReports;
import com.moewadeul.community.application.moderation.AdminCommunityCommentSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityCommentResponse(
        long commentId,
        long postId,
        String content,
        AdminModerationReports.Member author,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminModerationReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityCommentResponse from(AdminCommunityCommentSummary summary) {
        return new AdminCommunityCommentResponse(
                summary.commentId(),
                summary.postId(),
                summary.content(),
                new AdminModerationReports.Member(
                        summary.author().memberId(), summary.author().nickname()),
                summary.status(),
                summary.reportCount(),
                summary.reasonCounts(),
                AdminModerationReports.RecentReport.fromAll(summary.recentReports()),
                summary.statusChangedAt());
    }
}
