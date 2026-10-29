package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.AdminCommunityAuthor;
import com.moewadeul.community.application.moderation.AdminCommunityRecentReport;
import java.time.Instant;
import java.util.List;

public final class AdminCommunityReports {

    private AdminCommunityReports() {}

    public record Author(long memberId, String nickname) {

        static Author from(AdminCommunityAuthor author) {
            return new Author(author.memberId(), author.nickname());
        }
    }

    public record RecentReport(String reason, String content, Instant createdAt) {

        static List<RecentReport> fromAll(List<AdminCommunityRecentReport> reports) {
            return reports.stream()
                    .map(report -> new RecentReport(report.reason(), report.content(), report.createdAt()))
                    .toList();
        }
    }
}
