package com.moewadeul.member.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum SelfAgeGroup {
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTIES_PLUS;

    private static final String ALLOWED_NAMES =
            Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

    public static SelfAgeGroup parse(String name) {
        for (SelfAgeGroup value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "연령대는 %s 중 하나여야 해요.".formatted(ALLOWED_NAMES));
    }
}
