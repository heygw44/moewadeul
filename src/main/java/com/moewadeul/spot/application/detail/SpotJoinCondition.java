package com.moewadeul.spot.application.detail;

public record SpotJoinCondition(
        Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
