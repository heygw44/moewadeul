package com.moewadeul.spot.application.detail;

import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;

public record SpotBakjiDetail(
        String description,
        boolean hasWater,
        boolean hasToilet,
        BakjiSignalLevel signalLevel,
        long confirmationCount,
        long reporterId,
        String reporterNickname,
        String notice) {}
