package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.patch.PatchField;
import java.util.Objects;

public record BakjiUpdateCommand(
        PatchField<String> name,
        PatchField<Double> lat,
        PatchField<Double> lng,
        PatchField<String> description,
        PatchField<Boolean> hasWater,
        PatchField<Boolean> hasToilet,
        PatchField<String> signalLevel,
        PatchField<String> groundType) {

    public BakjiUpdateCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lat, "lat");
        Objects.requireNonNull(lng, "lng");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(hasWater, "hasWater");
        Objects.requireNonNull(hasToilet, "hasToilet");
        Objects.requireNonNull(signalLevel, "signalLevel");
        Objects.requireNonNull(groundType, "groundType");
    }
}
