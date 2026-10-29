package com.moewadeul.community.infra.moderation;

import com.moewadeul.common.moderation.ContentReportReason;
import java.time.Instant;

public record AdminCommunityRecentReportRow(
        long targetId, ContentReportReason reason, String content, Instant createdAt) {}
