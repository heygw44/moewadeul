package com.moewadeul.spot.infra.bakji;

public record BakjiDuplicateCondition(
        double centerLat,
        double centerLng,
        double radiusMeters,
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Long excludeSpotId,
        int limit) {}
