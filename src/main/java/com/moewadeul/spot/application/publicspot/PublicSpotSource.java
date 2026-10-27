package com.moewadeul.spot.application.publicspot;

public enum PublicSpotSource {
    GOCAMPING("CAMPSITE"),
    FOREST("FOREST"),
    FOREST_STANDARD("FOREST");

    private final String spotType;

    PublicSpotSource(String spotType) {
        this.spotType = spotType;
    }

    String spotType() {
        return spotType;
    }
}
