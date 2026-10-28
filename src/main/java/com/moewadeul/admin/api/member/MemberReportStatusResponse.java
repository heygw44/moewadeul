package com.moewadeul.admin.api.member;

import com.moewadeul.trust.application.report.MemberReportStatusResult;

public record MemberReportStatusResponse(long reportId, String status) {

    static MemberReportStatusResponse from(MemberReportStatusResult result) {
        return new MemberReportStatusResponse(result.reportId(), result.status());
    }
}
