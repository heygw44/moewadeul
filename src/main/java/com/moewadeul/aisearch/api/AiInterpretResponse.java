package com.moewadeul.aisearch.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.aisearch.application.SpotQueryConditions;
import java.util.Collection;
import java.util.List;

/** 문장에서 읽어 낸 조건. 읽어 내지 못한 조건은 키를 빼고, unparsed만 빈 배열이어도 항상 담는다. */
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

    // 집합은 순서가 없어서, 같은 문장에 같은 JSON이 나오도록 enum 선언 순서로 정렬한다.
    private static <E extends Enum<E>> List<String> names(Collection<E> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().sorted().map(Enum::name).toList();
    }
}
