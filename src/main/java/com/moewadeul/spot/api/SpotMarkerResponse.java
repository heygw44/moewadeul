package com.moewadeul.spot.api;

import com.moewadeul.spot.application.SpotMarker;
import com.moewadeul.spot.domain.SpotType;

public record SpotMarkerResponse(
        long spotId, SpotType type, String name, double lat, double lng, boolean parkWarning, boolean closedNow) {

    static SpotMarkerResponse from(SpotMarker marker) {
        return new SpotMarkerResponse(
                marker.spotId(),
                marker.type(),
                marker.name(),
                marker.lat(),
                marker.lng(),
                marker.parkWarning(),
                marker.closedNow());
    }
}
