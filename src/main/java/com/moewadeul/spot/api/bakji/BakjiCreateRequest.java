package com.moewadeul.spot.api.bakji;

import com.moewadeul.spot.application.bakji.BakjiReportCommand;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.bakji.BakjiContent;
import com.moewadeul.spot.domain.bakji.BakjiGroundType;
import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@InWeatherGrid
public record BakjiCreateRequest(
        @NotBlank(message = "이름을 입력해 주세요.") @Size(max = Spot.NAME_MAX_LENGTH, message = "이름은 100자 이하여야 해요.")
        String name,

        @NotNull(message = "위도를 입력해 주세요.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double lat,

        @NotNull(message = "경도를 입력해 주세요.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double lng,

        @Size(max = BakjiContent.DESCRIPTION_MAX_LENGTH, message = "설명은 2000자 이하여야 해요.")
        String description,

        @NotNull(message = "물이 있는지 골라 주세요.") Boolean hasWater,
        @NotNull(message = "화장실이 있는지 골라 주세요.") Boolean hasToilet,

        @Schema(implementation = BakjiSignalLevel.class, description = "통신 상태. 모르면 생략한다.")
        String signalLevel,

        @Schema(implementation = BakjiGroundType.class, description = "바닥 유형. 모르면 생략한다.")
        String groundType) {

    BakjiReportCommand toCommand() {
        return new BakjiReportCommand(name, lat, lng, description, hasWater, hasToilet, signalLevel, groundType);
    }
}
