package com.moewadeul.spot.api.map;

import com.moewadeul.spot.application.map.SpotNearbyItem;
import com.moewadeul.spot.domain.SpotType;

public record SpotNearbyItemResponse(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        boolean parkWarning,
        boolean closedNow,
        double distanceKm) {

    static SpotNearbyItemResponse from(SpotNearbyItem item) {
        return new SpotNearbyItemResponse(
                item.spotId(),
                item.type(),
                item.name(),
                item.lat(),
                item.lng(),
                item.parkWarning(),
                item.closedNow(),
                item.distanceKm());
    }
}
