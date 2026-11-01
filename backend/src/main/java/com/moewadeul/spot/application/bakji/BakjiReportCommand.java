package com.moewadeul.spot.application.bakji;

public record BakjiReportCommand(
        String name,
        double lat,
        double lng,
        String description,
        boolean hasWater,
        boolean hasToilet,
        String signalLevel,
        String groundType) {}
