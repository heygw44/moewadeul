package com.moewadeul.spot.domain;

import java.time.Instant;
import java.util.Objects;

public record ParkAreaJudgement(Long protectedAreaId, Instant checkedAt) {

    public ParkAreaJudgement {
        Objects.requireNonNull(checkedAt, "판정 시각이 없습니다.");
    }

    public static ParkAreaJudgement inside(long protectedAreaId, Instant checkedAt) {
        return new ParkAreaJudgement(protectedAreaId, checkedAt);
    }

    public static ParkAreaJudgement outside(Instant checkedAt) {
        return new ParkAreaJudgement(null, checkedAt);
    }

    public boolean parkWarning() {
        return protectedAreaId != null;
    }
}
