package com.moewadeul.common.error;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class EnumParser {

    private EnumParser() {}

    public static <E extends Enum<E>> Optional<E> find(Class<E> type, String name) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(name))
                .findFirst();
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String name, String message) {
        return find(type, name).orElseThrow(() -> invalidInput(message));
    }

    public static <E extends Enum<E>> E parseNullable(Class<E> type, String name, String message) {
        return name == null ? null : parse(type, name, message);
    }

    public static <E extends Enum<E>> E parseAmong(Set<E> allowed, String name, String message) {
        return allowed.stream()
                .filter(constant -> constant.name().equals(name))
                .findFirst()
                .orElseThrow(() -> invalidInput(message));
    }

    public static String names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
