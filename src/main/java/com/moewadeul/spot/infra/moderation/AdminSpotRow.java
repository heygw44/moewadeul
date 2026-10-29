package com.moewadeul.spot.infra.moderation;

import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.SpotType;
import java.time.Instant;

public record AdminSpotRow(
        long spotId,
        SpotType type,
        String name,
        SpotStatus status,
        double lat,
        double lng,
        boolean parkWarning,
        Long reporterId,
        String reporterNickname,
        long reportCount,
        Instant statusChangedAt) {}
