package com.moewadeul.trust.application.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminMemberReportDetail(
        AdminMemberReportSummary summary,
        String content,
        String resultNote,
        Long handledBy,
        Instant handledAt,
        Basecamp basecamp,
        Review companionReview,
        List<SanctionHistory> sanctionHistory) {

    public record Basecamp(long basecampId, String title, String status, LocalDate startDate) {}

    public record Review(String comment, List<String> tags, boolean rejoinWanted, boolean hidden) {}

    public record SanctionHistory(
            long sanctionId,
            String type,
            Integer level,
            String status,
            String reason,
            Instant startsAt,
            Instant endsAt,
            Instant liftedAt) {}
}
