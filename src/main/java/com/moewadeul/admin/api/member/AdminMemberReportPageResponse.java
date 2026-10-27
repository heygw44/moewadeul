package com.moewadeul.admin.api.member;

import com.moewadeul.trust.application.report.AdminMemberReportPage;
import java.util.List;

public record AdminMemberReportPageResponse(
        List<AdminMemberReportSummaryResponse> content, int page, int size, boolean hasNext) {

    static AdminMemberReportPageResponse from(AdminMemberReportPage page) {
        return new AdminMemberReportPageResponse(
                page.content().stream()
                        .map(AdminMemberReportSummaryResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.hasNext());
    }
}
