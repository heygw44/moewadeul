package com.moewadeul.basecamp.application;

import java.time.LocalDate;
import java.util.Set;

/**
 * 모집 중 베이스캠프 검색 조건이다. 지역은 지도 영역이나 반경 중 하나이고, 출발일 범위(경계 포함)는 null이면 그쪽을 제한하지 않는다.
 * hasVacancy가 true이면 정원이 아직 차지 않은 베이스캠프만 찾는다. page는 0부터 센다.
 *
 * <p>상세 조건은 비어 있거나 null이면 그 조건으로 거르지 않는다. spotTypes는 장소 유형, nights는 박 수(종료일 - 출발일)다.
 * maxTrustLevel이 있으면 합류 조건의 최소 신뢰 단계가 그 값 이하인 베이스캠프만 찾고, 최소 신뢰 단계가 없는 베이스캠프는 단계 1로 본다.
 * noAgeLimit은 연령대 조건이 없는 것만, noSameGenderOnly는 동성만 받는 조건이 없는 것만 찾는다.
 */
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

    /** 검색할 지역이다. */
    public sealed interface Region permits Area, Radius {}

    /** 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 정하는 지도 영역이다. */
    public record Area(double swLat, double swLng, double neLat, double neLng) implements Region {}

    /** 중심(lat, lng)에서 radiusKm 안의 지역이다. */
    public record Radius(double lat, double lng, double radiusKm) implements Region {}
}
