package com.moewadeul.spot.infra.moderation;

import com.moewadeul.spot.domain.bakji.BakjiReportReason;
import java.time.Instant;

public record AdminSpotRecentReportRow(long spotId, BakjiReportReason reason, String content, Instant createdAt) {}
