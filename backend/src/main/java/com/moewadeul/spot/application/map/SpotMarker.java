package com.moewadeul.spot.application.map;

import com.moewadeul.spot.domain.SpotType;

public record SpotMarker(
        long spotId, SpotType type, String name, double lat, double lng, boolean parkWarning, boolean closedNow) {}
