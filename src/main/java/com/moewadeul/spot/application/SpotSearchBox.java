package com.moewadeul.spot.application;

public record SpotSearchBox(double swLat, double swLng, double neLat, double neLng) {

    private static final double KM_PER_DEGREE = 111.0;

    private static final double BOX_MARGIN = 1.1;

    private static final double MIN_COS_LATITUDE = 1e-6;

    public static SpotSearchBox around(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE * BOX_MARGIN;
        double cosLat = Math.cos(Math.toRadians(lat));
        double swLng = -180;
        double neLng = 180;
        if (cosLat > MIN_COS_LATITUDE) {
            double lngDelta = radiusKm / (KM_PER_DEGREE * cosLat) * BOX_MARGIN;
            swLng = Math.max(lng - lngDelta, -180);
            neLng = Math.min(lng + lngDelta, 180);
        }
        return new SpotSearchBox(Math.max(lat - latDelta, -90), swLng, Math.min(lat + latDelta, 90), neLng);
    }
}
