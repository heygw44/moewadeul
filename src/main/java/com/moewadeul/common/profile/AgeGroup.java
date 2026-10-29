package com.moewadeul.common.profile;

import java.util.Arrays;

public enum AgeGroup {
    TWENTIES(20),
    THIRTIES(30),
    FORTIES(40),
    FIFTIES(50),
    SIXTIES_PLUS(60);

    private final int decade;

    AgeGroup(int decade) {
        this.decade = decade;
    }

    public int decade() {
        return decade;
    }

    public static AgeGroup ofDecade(int decade) {
        return Arrays.stream(values())
                .filter(group -> group.decade == decade)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("연령대는 20~60 사이의 십 단위여야 합니다."));
    }

    public static AgeGroup ofAge(int age) {
        if (age < 30) {
            return TWENTIES;
        }
        if (age < 40) {
            return THIRTIES;
        }
        if (age < 50) {
            return FORTIES;
        }
        if (age < 60) {
            return FIFTIES;
        }
        return SIXTIES_PLUS;
    }
}
