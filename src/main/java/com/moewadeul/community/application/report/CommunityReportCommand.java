package com.moewadeul.community.application.report;

import com.moewadeul.community.domain.report.CommunityReportReason;

public record CommunityReportCommand(CommunityReportReason reason, String content) {}
