package com.moewadeul.trust.api.report;

import com.moewadeul.trust.domain.report.ReportStatus;

public record MemberReportCreatedResponse(long reportId, ReportStatus status) {}
