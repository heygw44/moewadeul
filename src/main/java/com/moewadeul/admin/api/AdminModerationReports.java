package com.moewadeul.admin.api;

import java.time.Instant;
import java.util.List;

public final class AdminModerationReports {

    private AdminModerationReports() {}

    public record Member(long memberId, String nickname) {}

    public record RecentReport(String reason, String content, Instant createdAt) {

        public static List<RecentReport> fromAll(List<com.moewadeul.common.moderation.RecentReport> reports) {
            return reports.stream()
                    .map(report -> new RecentReport(report.reason(), report.content(), report.createdAt()))
                    .toList();
        }
    }
}
