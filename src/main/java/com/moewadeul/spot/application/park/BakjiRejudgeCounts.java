package com.moewadeul.spot.application.park;

public record BakjiRejudgeCounts(int judged, int newlyWarned, int cleared) {

    public BakjiRejudgeCounts plus(BakjiRejudgeCounts other) {
        return new BakjiRejudgeCounts(judged + other.judged, newlyWarned + other.newlyWarned, cleared + other.cleared);
    }
}
