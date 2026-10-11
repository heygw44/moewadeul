package com.moewadeul.trust.api;

import com.moewadeul.trust.domain.ReportStatus;

public record MemberReportCreatedResponse(long reportId, ReportStatus status) {}
