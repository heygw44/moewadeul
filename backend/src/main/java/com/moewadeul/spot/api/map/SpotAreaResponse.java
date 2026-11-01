package com.moewadeul.spot.api.map;

import com.moewadeul.spot.application.map.SpotAreaResult;
import java.util.List;

public record SpotAreaResponse(List<SpotMarkerResponse> markers, List<SpotClusterResponse> clusters) {

    static SpotAreaResponse from(SpotAreaResult result) {
        List<SpotMarkerResponse> markers =
                result.markers().stream().map(SpotMarkerResponse::from).toList();
        List<SpotClusterResponse> clusters =
                result.clusters().stream().map(SpotClusterResponse::from).toList();
        return new SpotAreaResponse(markers, clusters);
    }
}
