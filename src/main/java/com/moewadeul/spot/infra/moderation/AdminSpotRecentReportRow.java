package com.moewadeul.spot.infra.moderation;

import java.time.Instant;

public record AdminSpotRecentReportRow(long spotId, String reason, String content, Instant createdAt) {}
