package com.moewadeul.admin.api.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moewadeul.community.application.moderation.AdminCommunityAuthor;
import com.moewadeul.community.application.moderation.AdminCommunityReasonCounts;
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

    public record ReasonCounts(
            @JsonProperty("SPAM") long spam,
            @JsonProperty("ABUSE") long abuse,
            @JsonProperty("ILLEGAL_CAMPING") long illegalCamping,
            @JsonProperty("PRIVACY") long privacy,
            @JsonProperty("MONEY_SCAM") long moneyScam,
            @JsonProperty("OTHER") long other) {

        static ReasonCounts from(AdminCommunityReasonCounts counts) {
            return new ReasonCounts(
                    counts.spam(),
                    counts.abuse(),
                    counts.illegalCamping(),
                    counts.privacy(),
                    counts.moneyScam(),
                    counts.other());
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
