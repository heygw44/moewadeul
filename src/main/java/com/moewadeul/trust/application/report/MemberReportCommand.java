package com.moewadeul.trust.application.report;

import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportType;

public record MemberReportCommand(
        long targetMemberId,
        long basecampId,
        ReportKind kind,
        Long companionReviewId,
        ReportType type,
        String content) {}
