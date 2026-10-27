package com.moewadeul.community.infra.moderation;

import java.time.Instant;

public record AdminCommunityRecentReportRow(long targetId, String reason, String content, Instant createdAt) {}
