package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.application.query.BasecampSpotType;
import java.time.LocalDate;
import java.util.Set;

public record BasecampSearchCondition(
        Area area,
        Nearby nearby,
        LocalDate fromDate,
        LocalDate toDate,
        boolean hasVacancy,
        Set<BasecampSpotType> spotTypes,
        Set<Integer> nights,
        Integer maxTrustLevel,
        boolean noAgeLimit,
        boolean noSameGenderOnly) {

    public BasecampSearchCondition {
        spotTypes = Set.copyOf(spotTypes);
        nights = Set.copyOf(nights);
        if ((area == null) == (nearby == null)) {
            throw new IllegalArgumentException("지역은 영역과 반경 중 하나만 있어야 합니다.");
        }
    }

    public record Area(double swLat, double swLng, double neLat, double neLng) {}

    public record Nearby(
            double centerLat,
            double centerLng,
            double radiusMeters,
            double swLat,
            double swLng,
            double neLat,
            double neLng) {}
}
