package com.moewadeul.spot.infra.moderation;

import java.time.Instant;

public record AdminSpotRow(
        long spotId,
        String type,
        String name,
        String status,
        double lat,
        double lng,
        boolean parkWarning,
        Long reporterId,
        String reporterNickname,
        long reportCount,
        Instant statusChangedAt) {}
