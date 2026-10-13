package com.moewadeul.memory.domain;

import java.util.Arrays;
import java.util.Optional;

/** 추억 기록을 누가 볼 수 있는지. 새 기록은 작성자만 보는 PRIVATE로 시작하고, 작성자가 언제든 바꿀 수 있다. */
public enum MemoryVisibility {
    PRIVATE,
    PUBLIC;

    /** 호출하면 이름이 name인 값을 돌려준다. 모르는 이름이거나 null이면 빈 값이다. */
    public static Optional<MemoryVisibility> from(String name) {
        return Arrays.stream(values())
                .filter(value -> value.name().equals(name))
                .findFirst();
    }
}
