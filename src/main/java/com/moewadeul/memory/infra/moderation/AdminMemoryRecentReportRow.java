package com.moewadeul.memory.infra.moderation;

import com.moewadeul.common.moderation.ContentReportReason;
import java.time.Instant;

public record AdminMemoryRecentReportRow(
        long memoryId, ContentReportReason reason, String content, Instant createdAt) {}
