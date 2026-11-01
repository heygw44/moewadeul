package com.moewadeul.common.profile;

import java.util.Arrays;
import java.util.Optional;

public enum TrustLevel {
    EMAIL_VERIFIED(0),
    IDENTITY_VERIFIED(1),
    TRUSTED(2);

    private final int level;

    TrustLevel(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static TrustLevel of(int level) {
        return find(level).orElseThrow(() -> new IllegalArgumentException("신뢰 단계는 0~2 사이여야 합니다."));
    }

    public static Optional<TrustLevel> find(int level) {
        return Arrays.stream(values()).filter(value -> value.level == level).findFirst();
    }

    public boolean atLeast(TrustLevel other) {
        return level >= other.level;
    }
}
