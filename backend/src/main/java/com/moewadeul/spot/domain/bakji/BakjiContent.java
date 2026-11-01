package com.moewadeul.spot.domain.bakji;

public record BakjiContent(
        String description,
        boolean hasWater,
        boolean hasToilet,
        BakjiSignalLevel signalLevel,
        BakjiGroundType groundType) {

    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    public BakjiContent {
        requireValidDescription(description);
    }

    public static void requireValidDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
