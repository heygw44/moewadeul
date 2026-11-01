package com.moewadeul.spot.application.map;

import com.moewadeul.spot.domain.SpotType;

public record SpotNearbyItem(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        boolean parkWarning,
        boolean closedNow,
        double distanceKm) {}
