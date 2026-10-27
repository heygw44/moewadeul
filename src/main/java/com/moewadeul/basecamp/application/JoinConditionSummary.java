package com.moewadeul.basecamp.application;

public record JoinConditionSummary(
        Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {}
