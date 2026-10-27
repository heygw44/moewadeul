package com.moewadeul.aisearch.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.aisearch.application.SpotQueryConditions;
import java.util.Collection;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiInterpretResponse(
        String region,
        Double radiusKm,
        List<String> types,
        Boolean hasWater,
        Boolean hasToilet,
        Boolean excludeWarning,
        Boolean hideClosed,
        List<String> settings,
        List<String> unparsed) {

    static AiInterpretResponse from(SpotQueryConditions conditions) {
        return new AiInterpretResponse(
                conditions.region(),
                conditions.radiusKm(),
                names(conditions.types()),
                onlyTrue(conditions.hasWater()),
                onlyTrue(conditions.hasToilet()),
                onlyTrue(conditions.excludeWarning()),
                onlyTrue(conditions.hideClosed()),
                names(conditions.settings()),
                conditions.unparsed());
    }

    private static Boolean onlyTrue(boolean value) {
        return value ? Boolean.TRUE : null;
    }

    private static <E extends Enum<E>> List<String> names(Collection<E> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().sorted().map(Enum::name).toList();
    }
}
