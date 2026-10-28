package com.moewadeul.trust.domain.identity;

import java.util.regex.Pattern;

public record CiHash(String value) {

    public static final int LENGTH = 64;

    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{" + LENGTH + "}");

    public CiHash {
        if (value == null || !LOWER_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("CI 해시는 소문자 16진수 " + LENGTH + "자여야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "CiHash[****]";
    }
}
