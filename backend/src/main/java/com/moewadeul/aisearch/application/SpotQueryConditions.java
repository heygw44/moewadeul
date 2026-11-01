package com.moewadeul.aisearch.application;

import java.util.List;
import java.util.Set;

public record SpotQueryConditions(
        String region,
        Double radiusKm,
        Set<AiSpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        Set<AiSpotSetting> settings,
        List<String> unparsed) {

    public static final double MAX_RADIUS_KM = 50;

    public SpotQueryConditions {
        if (region != null && region.isBlank()) {
            region = null;
        }
        if (radiusKm != null && radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be positive");
        }
        if (radiusKm != null && radiusKm > MAX_RADIUS_KM) {
            radiusKm = MAX_RADIUS_KM;
        }
        types = types == null ? Set.of() : Set.copyOf(types);
        settings = settings == null ? Set.of() : Set.copyOf(settings);
        unparsed = unparsed == null ? List.of() : List.copyOf(unparsed);
    }
}
