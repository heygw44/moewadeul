package com.moewadeul.spot.api;

import com.moewadeul.spot.application.SpotCluster;

public record SpotClusterResponse(double lat, double lng, long count) {

    static SpotClusterResponse from(SpotCluster cluster) {
        return new SpotClusterResponse(cluster.lat(), cluster.lng(), cluster.count());
    }
}
