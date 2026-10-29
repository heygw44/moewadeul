package com.moewadeul.community.application.report;

import com.moewadeul.common.moderation.ContentReportReason;

public record CommunityReportCommand(ContentReportReason reason, String content) {}
