package com.moewadeul.admin.api.community;

import com.moewadeul.admin.api.AdminModerationReports;
import com.moewadeul.community.application.moderation.AdminCommunityPostSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityPostResponse(
        long postId,
        String title,
        String content,
        AdminModerationReports.Member author,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminModerationReports.RecentReport> recentReports,
        Instant statusChangedAt) {

    static AdminCommunityPostResponse from(AdminCommunityPostSummary summary) {
        return new AdminCommunityPostResponse(
                summary.postId(),
                summary.title(),
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
