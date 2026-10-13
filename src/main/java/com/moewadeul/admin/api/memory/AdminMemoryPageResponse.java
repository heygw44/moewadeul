package com.moewadeul.admin.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moewadeul.admin.api.community.AdminCommunityReports;
import com.moewadeul.memory.application.moderation.AdminMemoryPage;
import com.moewadeul.memory.application.moderation.AdminMemorySummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자 추억 기록 검토 목록의 한 페이지 응답이다. 전체 개수는 없고 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record AdminMemoryPageResponse(List<Item> content, int page, int size, boolean hasNext) {

    static AdminMemoryPageResponse from(AdminMemoryPage page) {
        return new AdminMemoryPageResponse(
                page.content().stream().map(Item::from).toList(), page.page(), page.size(), page.hasNext());
    }

    // 관리자에게도 정확한 좌표는 주지 않고 대략 좌표(approxLat, approxLng)만 준다. 글이 없으면 content 필드를 뺀다.
    public record Item(
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

        static Item from(AdminMemorySummary summary) {
            return new Item(
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
    }

    /** 기록 안 순서대로 담는다. url은 응답마다 새로 서명한 조회 URL이다. */
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
