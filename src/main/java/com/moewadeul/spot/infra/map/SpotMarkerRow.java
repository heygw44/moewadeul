package com.moewadeul.spot.infra.map;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;

public record SpotMarkerRow(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        boolean parkWarning,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil) {}
