package com.moewadeul.admin.api;

import com.moewadeul.trust.application.MemberReportStatusResult;

/** 신고 검토 시작·기각의 응답. status는 처리 뒤의 신고 상태다. */
public record MemberReportStatusResponse(long reportId, String status) {

    static MemberReportStatusResponse from(MemberReportStatusResult result) {
        return new MemberReportStatusResponse(result.reportId(), result.status());
    }
}
