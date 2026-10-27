package com.moewadeul.weather.domain;

import java.util.Comparator;
import java.util.List;

public class MidTermRegions {

    private final List<MidTermRegion> regions;

    public MidTermRegions(List<MidTermRegion> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("중기예보 구역 목록이 비어 있다");
        }
        this.regions = List.copyOf(regions);
    }

    public MidTermRegion nearest(double lat, double lng) {
        return regions.stream()
                .min(Comparator.comparingDouble(region -> squaredDistance(region, lat, lng)))
                .orElseThrow();
    }

    private static double squaredDistance(MidTermRegion region, double lat, double lng) {
        double dLat = region.lat() - lat;
        double dLng = (region.lng() - lng) * Math.cos(Math.toRadians(lat));
        return dLat * dLat + dLng * dLng;
    }
}
