package com.moewadeul.spot.api.map;

import com.moewadeul.spot.application.map.SpotCluster;

public record SpotClusterResponse(double lat, double lng, long count) {

    static SpotClusterResponse from(SpotCluster cluster) {
        return new SpotClusterResponse(cluster.lat(), cluster.lng(), cluster.count());
    }
}
