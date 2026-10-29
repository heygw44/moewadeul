package com.moewadeul.spot.application.publicspot;

import com.moewadeul.spot.domain.SpotType;

public enum PublicSpotSource {
    GOCAMPING(SpotType.CAMPSITE),
    FOREST(SpotType.FOREST),
    FOREST_STANDARD(SpotType.FOREST);

    private final SpotType spotType;

    PublicSpotSource(SpotType spotType) {
        this.spotType = spotType;
    }

    SpotType spotType() {
        return spotType;
    }
}
