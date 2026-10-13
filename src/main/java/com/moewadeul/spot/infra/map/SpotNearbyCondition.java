package com.moewadeul.spot.infra.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.Set;

/**
 * {@link SpotNearbyMapper}가 반경 검색에 쓰는 조건. 중심(centerLat, centerLng)에서 radiusMeters 안의 장소를 찾고, 반경 경계 위의 장소도
 * 포함한다.
 *
 * <p>남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓴다. 그래서 호출하는 쪽은 반경 원을
 * 모두 덮을 만큼 넉넉하게 사각형을 잡아야 한다.
 *
 * <p>types가 비어 있으면 매퍼는 유형으로 거르지 않는다. settings가 비어 있지 않으면 입지가 하나라도 맞는 장소만 남긴다. 입지는 고캠핑 야영장에만 있어서 박지, 자연휴양림, 입지가 빈 야영장은
 * 빠진다. hasWater와 hasToilet은 박지에만 적용하고, excludeWarning이 {@code true}면 매퍼가 공원 경계 경고가 붙은 장소를 뺀다.
 *
 * <p>hideClosed가 {@code true}면 매퍼가 today(한국 날짜)에 휴장인 공공 장소를 뺀다. 휴장 판정 규칙은 {@code SpotClosure}와 같다.
 */
public record SpotNearbyCondition(
        double centerLat,
        double centerLng,
        double radiusMeters,
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        LocalDate today) {

    public SpotNearbyCondition {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
    }
}
