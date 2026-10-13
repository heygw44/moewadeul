package com.moewadeul.spot.application.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.util.Set;

/**
 * 지도 화면 영역 조회 조건. 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 사각형 안의 장소를 찾고, 경계선 위의 장소도 포함한다.
 *
 * <p>types가 비어 있으면 모든 유형을 찾는다. settings가 비어 있지 않으면 입지가 하나라도 맞는 장소만 남긴다. 입지는 고캠핑 야영장에만 있어서, 박지와 자연휴양림, 입지가 빈 야영장은 빠진다.
 * hasWater와 hasToilet은 박지에만 적용한다. 그래서 {@code true}여도 야영장과 자연휴양림은 시설과 상관없이 남는다. excludeWarning이 {@code true}면 공원 경계 경고가
 * 붙은 장소를 뺀다. hideClosed가 {@code true}면 조회한 날 휴장인 야영장과 자연휴양림을 뺀다.
 */
public record SpotAreaQuery(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed) {

    public SpotAreaQuery {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
    }
}
