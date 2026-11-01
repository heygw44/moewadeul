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

    private static final double MAX_SIDE_RATIO = 10;

    @AssertTrue(message = "남서쪽 위도는 북동쪽 위도보다 작아야 해요.")
    public boolean isLatitudeOrdered() {
        return swLat == null || neLat == null || swLat < neLat;
    }

    @AssertTrue(message = "남서쪽 경도는 북동쪽 경도보다 작아야 해요.")
    public boolean isLongitudeOrdered() {
        return swLng == null || neLng == null || swLng < neLng;
    }

    @AssertTrue(message = "지도 영역이 너무 가늘고 길어요. 긴 변은 짧은 변의 10배 이하여야 해요.")
    public boolean isAreaProportioned() {
        if (swLat == null || swLng == null || neLat == null || neLng == null) {
            return true;
        }
        double height = neLat - swLat;
        double width = neLng - swLng;
        if (height <= 0 || width <= 0) {
            return true;
        }
        return Math.max(height, width) <= Math.min(height, width) * MAX_SIDE_RATIO;
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
