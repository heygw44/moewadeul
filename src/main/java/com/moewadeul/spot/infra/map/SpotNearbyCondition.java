package com.moewadeul.spot.infra.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.Set;

public record SpotNearbyCondition(
        double centerLat,
        double centerLng,
        double radiusMeters,
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        LocalDate today) {

    public SpotNearbyCondition {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
    }
}
