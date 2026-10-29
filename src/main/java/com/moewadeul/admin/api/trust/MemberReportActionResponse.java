package com.moewadeul.admin.api.trust;

import com.moewadeul.trust.application.report.MemberReportActionResult;

public record MemberReportActionResponse(long reportId, String status, Long sanctionId) {

    static MemberReportActionResponse from(MemberReportActionResult result) {
        return new MemberReportActionResponse(result.reportId(), result.status(), result.sanctionId());
    }
}
