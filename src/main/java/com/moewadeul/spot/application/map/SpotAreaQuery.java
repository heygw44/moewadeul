package com.moewadeul.spot.application.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.util.Set;

public record SpotAreaQuery(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed) {

    public SpotAreaQuery {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
    }
}
