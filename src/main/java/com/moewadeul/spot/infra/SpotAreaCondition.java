package com.moewadeul.spot.infra;

import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.Set;

/**
 * {@link SpotAreaMapper}가 지도 영역 조회에 쓰는 조건. 남서(swLat, swLng)와 북동(neLat, neLng) 꼭짓점이 만드는 사각형이 영역이고, 경계선 위의
 * 장소도 포함한다.
 *
 * <p>types가 비어 있으면 매퍼는 유형으로 거르지 않는다. hasWater와 hasToilet은 박지에만 적용하고, excludeWarning이 {@code true}면 매퍼가 공원
 * 경계 경고가 붙은 장소를 뺀다.
 *
 * <p>hideClosed가 {@code true}면 매퍼가 today(한국 날짜)에 휴장인 공공 장소를 뺀다. 휴장 판정 규칙은 {@code SpotClosure}와 같다.
 */
public record SpotAreaCondition(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        Set<SpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        LocalDate today) {

    public SpotAreaCondition {
        types = Set.copyOf(types);
    }

    /**
     * 호출하면 묶음 집계의 조합별 표가 필요한 조건인지 돌려준다. 유형, 물, 화장실, 경고 필터 가운데 하나라도 켜면 {@code true}다. 휴장 제외는 날짜에 따라
     * 바뀌어서 집계 표에 없으므로 여기에 넣지 않는다.
     */
    public boolean filtersCellAttributes() {
        return !types.isEmpty() || hasWater || hasToilet || excludeWarning;
    }
}
