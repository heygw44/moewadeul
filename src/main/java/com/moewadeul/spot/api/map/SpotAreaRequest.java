package com.moewadeul.spot.api.map;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.application.map.SpotAreaQuery;
import com.moewadeul.spot.domain.SpotType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

// 지도 화면이 쿼리 파라미터로 보내는 영역 조회 조건이다. 기본형 숫자는 값이 빠진 상태를 나타낼 수 없다.
// 그래서 래퍼 타입으로 받고, 서버는 빠진 값을 @NotNull 위반으로 보고 필드 오류로 돌려준다.
// types와 settings는 쉼표로 구분한 문자열(CAMPSITE,BAKJI)과 같은 이름을 여러 번 쓴 형태를 모두 받는다.
public record SpotAreaRequest(
        @NotNull(message = "남서쪽 위도를 입력해 주세요.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double swLat,

        @NotNull(message = "남서쪽 경도를 입력해 주세요.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double swLng,

        @NotNull(message = "북동쪽 위도를 입력해 주세요.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double neLat,

        @NotNull(message = "북동쪽 경도를 입력해 주세요.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double neLng,

        @NotNull(message = "지도 확대 수준을 입력해 주세요.")
        @Min(value = 1, message = "지도 확대 수준은 1 이상이어야 해요.")
        @Max(value = 14, message = "지도 확대 수준은 14 이하여야 해요.")
        Integer zoom,

        Set<@NotNull(message = "장소 유형에 빈 값이 있어요.") SpotType> types,
        Set<@NotNull(message = "입지에 빈 값이 있어요.") PublicSpotSetting> settings,
        Boolean hasWater,
        Boolean hasToilet,
        Boolean excludeWarning,
        Boolean hideClosed) {

    // 위도나 경도가 빠졌으면 @NotNull이 따로 오류를 내므로, 여기서는 둘 다 있을 때만 순서를 본다.
    @AssertTrue(message = "남서쪽 위도는 북동쪽 위도보다 작아야 해요.")
    public boolean isLatitudeOrdered() {
        return swLat == null || neLat == null || swLat < neLat;
    }

    @AssertTrue(message = "남서쪽 경도는 북동쪽 경도보다 작아야 해요.")
    public boolean isLongitudeOrdered() {
        return swLng == null || neLng == null || swLng < neLng;
    }

    SpotAreaQuery toQuery() {
        return new SpotAreaQuery(
                swLat,
                swLng,
                neLat,
                neLng,
                types == null ? Set.of() : types,
                settings == null ? Set.of() : settings,
                Boolean.TRUE.equals(hasWater),
                Boolean.TRUE.equals(hasToilet),
                Boolean.TRUE.equals(excludeWarning),
                Boolean.TRUE.equals(hideClosed));
    }
}
