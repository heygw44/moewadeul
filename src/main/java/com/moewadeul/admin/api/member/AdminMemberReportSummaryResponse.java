package com.moewadeul.admin.api.member;

import com.moewadeul.trust.application.report.AdminMemberReportSummary;
import java.time.Instant;

public record AdminMemberReportSummaryResponse(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        MemberRef reporter,
        MemberRef target,
        long basecampId,
        Instant createdAt) {

    public record MemberRef(long memberId, String nickname) {}

    static AdminMemberReportSummaryResponse from(AdminMemberReportSummary summary) {
        return new AdminMemberReportSummaryResponse(
                summary.reportId(),
                summary.kind(),
                summary.type(),
                summary.urgent(),
                summary.status(),
                new MemberRef(summary.reporterId(), summary.reporterNickname()),
                new MemberRef(summary.targetId(), summary.targetNickname()),
                summary.basecampId(),
                summary.createdAt());
    }
}
