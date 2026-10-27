package com.moewadeul.spot.application.map;

import java.util.List;

public record SpotAreaResult(List<SpotMarker> markers, List<SpotCluster> clusters) {

    public SpotAreaResult {
        markers = List.copyOf(markers);
        clusters = List.copyOf(clusters);
    }
}
