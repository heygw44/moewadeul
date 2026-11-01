package com.moewadeul.spot.application.map;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotErrorCode;
import com.moewadeul.spot.domain.SpotType;
import java.util.Set;

public record SpotNearbyQuery(
        double lat,
        double lng,
        double radiusKm,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        int page,
        int size) {

    public static final double MAX_RADIUS_KM = 50;

    public SpotNearbyQuery {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
        if (radiusKm > MAX_RADIUS_KM) {
            throw new BusinessException(SpotErrorCode.SPOT_RADIUS_TOO_LARGE);
        }
    }
}
