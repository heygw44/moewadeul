package com.moewadeul.spot.application.park;

import java.util.Objects;

public record ProtectedAreaCommand(String name, ProtectedAreaType areaType, String multiPolygonWkt) {

    public ProtectedAreaCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(areaType, "areaType");
        Objects.requireNonNull(multiPolygonWkt, "multiPolygonWkt");
    }
}
