package com.moewadeul.basecamp.application.query;

import java.time.LocalDate;
import java.util.Set;

public record BasecampSearchQuery(
        Region region,
        LocalDate fromDate,
        LocalDate toDate,
        boolean hasVacancy,
        Set<BasecampSpotType> spotTypes,
        Set<Integer> nights,
        Integer maxTrustLevel,
        boolean noAgeLimit,
        boolean noSameGenderOnly,
        int page,
        int size) {

    public BasecampSearchQuery {
        spotTypes = Set.copyOf(spotTypes);
        nights = Set.copyOf(nights);
    }

    public sealed interface Region permits Area, Radius {}

    public record Area(double swLat, double swLng, double neLat, double neLng) implements Region {}

    public record Radius(double lat, double lng, double radiusKm) implements Region {}
}
