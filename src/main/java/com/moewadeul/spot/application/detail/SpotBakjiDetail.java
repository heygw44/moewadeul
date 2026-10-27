package com.moewadeul.spot.application.detail;

public record SpotBakjiDetail(
        String description,
        boolean hasWater,
        boolean hasToilet,
        String signalLevel,
        long confirmationCount,
        long reporterId,
        String reporterNickname,
        String notice) {}
