package com.moewadeul.spot.api.map;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.application.map.SpotNearbyQuery;
import com.moewadeul.spot.domain.SpotType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SpotNearbyRequest(
        @NotNull(message = "중심 위도를 입력해 주세요.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double lat,

        @NotNull(message = "중심 경도를 입력해 주세요.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double lng,

        @NotNull(message = "반경을 입력해 주세요.") @DecimalMin(value = "0", inclusive = false, message = "반경은 0보다 커야 해요.")
        Double radiusKm,

        Set<@NotNull(message = "장소 유형에 빈 값이 있어요.") SpotType> types,
        Set<@NotNull(message = "입지에 빈 값이 있어요.") PublicSpotSetting> settings,
        Boolean hasWater,
        Boolean hasToilet,
        Boolean excludeWarning,
        Boolean hideClosed,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    SpotNearbyQuery toQuery() {
        return new SpotNearbyQuery(
                lat,
                lng,
                radiusKm,
                types == null ? Set.of() : types,
                settings == null ? Set.of() : settings,
                Boolean.TRUE.equals(hasWater),
                Boolean.TRUE.equals(hasToilet),
                Boolean.TRUE.equals(excludeWarning),
                Boolean.TRUE.equals(hideClosed),
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }
}
