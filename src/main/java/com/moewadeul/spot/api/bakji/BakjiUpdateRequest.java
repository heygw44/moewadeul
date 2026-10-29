package com.moewadeul.spot.api.bakji;

import com.moewadeul.common.patch.PatchField;
import com.moewadeul.spot.application.bakji.BakjiUpdateCommand;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.bakji.BakjiContent;
import io.swagger.v3.oas.annotations.media.Schema;

public record BakjiUpdateRequest(
        @Schema(
                implementation = String.class,
                maxLength = Spot.NAME_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> name,

        @Schema(
                implementation = Double.class,
                minimum = "-90",
                maximum = "90",
                description = "lng와 함께 보내야 한다. null로 보내면 400이다.")
        PatchField<Double> lat,

        @Schema(
                implementation = Double.class,
                minimum = "-180",
                maximum = "180",
                description = "lat와 함께 보내야 한다. null로 보내면 400이다.")
        PatchField<Double> lng,

        @Schema(
                implementation = String.class,
                maxLength = BakjiContent.DESCRIPTION_MAX_LENGTH,
                description = "null로 보내면 지운다.")
        PatchField<String> description,

        @Schema(implementation = Boolean.class, description = "null로 보내면 400이다.")
        PatchField<Boolean> hasWater,

        @Schema(implementation = Boolean.class, description = "null로 보내면 400이다.")
        PatchField<Boolean> hasToilet,

        @Schema(
                implementation = String.class,
                description = "통신 상태. null로 보내면 지운다.",
                allowableValues = {"NONE", "WEAK", "GOOD"})
        PatchField<String> signalLevel,

        @Schema(
                implementation = String.class,
                description = "바닥 유형. null로 보내면 지운다.",
                allowableValues = {"SOIL", "GRASS", "GRAVEL", "SAND", "ROCK", "DECK"})
        PatchField<String> groundType) {

    public BakjiUpdateRequest {
        name = absentIfNull(name);
        lat = absentIfNull(lat);
        lng = absentIfNull(lng);
        description = absentIfNull(description);
        hasWater = absentIfNull(hasWater);
        hasToilet = absentIfNull(hasToilet);
        signalLevel = absentIfNull(signalLevel);
        groundType = absentIfNull(groundType);
    }

    BakjiUpdateCommand toCommand() {
        return new BakjiUpdateCommand(name, lat, lng, description, hasWater, hasToilet, signalLevel, groundType);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}
