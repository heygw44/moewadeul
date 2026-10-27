package com.moewadeul.memory.infra.moderation;

import java.time.Instant;

public record AdminMemoryRecentReportRow(long memoryId, String reason, String content, Instant createdAt) {}
