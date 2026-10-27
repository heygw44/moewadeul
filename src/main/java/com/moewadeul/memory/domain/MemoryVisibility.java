package com.moewadeul.memory.domain;

import java.util.Arrays;
import java.util.Optional;

public enum MemoryVisibility {
    PRIVATE,
    PUBLIC;

    public static Optional<MemoryVisibility> from(String name) {
        return Arrays.stream(values())
                .filter(value -> value.name().equals(name))
                .findFirst();
    }
}
