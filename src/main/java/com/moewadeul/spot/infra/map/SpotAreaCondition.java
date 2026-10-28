package com.moewadeul.spot.infra.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.Set;

public record SpotAreaCondition(
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

    public SpotAreaCondition {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
    }

    public boolean filtersSettings() {
        return !settings.isEmpty();
    }

    public boolean filtersCellAttributes() {
        return !types.isEmpty() || hasWater || hasToilet || excludeWarning;
    }
}
