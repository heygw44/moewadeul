package com.moewadeul.spot.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.domain.SpotErrorCode;
import com.moewadeul.spot.domain.SpotType;
import java.util.Set;

/**
 * 반경 검색 조건. 중심(lat, lng)에서 radiusKm 안에 있는 장소를 찾고, page(0부터)와 size로 한 페이지를 고른다.
 *
 * <p>반경이 {@value #MAX_RADIUS_KM}km를 넘으면 생성자가 {@link SpotErrorCode#SPOT_RADIUS_TOO_LARGE}로 거부한다. 그래서 컨트롤러가 요청을 이
 * 조건으로 바꾸는 순간 큰 반경은 서비스까지 가지 않는다.
 *
 * <p>필터는 지도 영역 조회와 같다. types가 비어 있으면 모든 유형을 찾고, settings가 비어 있지 않으면 입지가 하나라도 맞는 장소만 남긴다. 입지는 고캠핑 야영장에만 있어서, 박지와 자연휴양림,
 * 입지가 빈 야영장은 빠진다. hasWater와 hasToilet은 박지에만 적용한다. excludeWarning이 {@code true}면 공원 경계 경고가 붙은 장소를 뺀다. hideClosed가 {@code
 * true}면 조회한 날 휴장인 야영장과 자연휴양림을 뺀다.
 */
public record SpotNearbyQuery(
        double lat,
        double lng,
        double radiusKm,
        Set<SpotType> types,
        Set<PublicSpotSetting> settings,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        int page,
        int size) {

    public static final double MAX_RADIUS_KM = 50;

    public SpotNearbyQuery {
        types = Set.copyOf(types);
        settings = Set.copyOf(settings);
        if (radiusKm > MAX_RADIUS_KM) {
            throw new BusinessException(SpotErrorCode.SPOT_RADIUS_TOO_LARGE);
        }
    }
}
