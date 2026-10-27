package com.moewadeul.trust.domain.identity;

import java.util.Optional;

public enum AgeGroup {
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTIES_PLUS;

    public static Optional<AgeGroup> fromAge(int age) {
        if (age < IdentityVerification.ADULT_AGE) {
            return Optional.empty();
        }
        if (age < 30) {
            return Optional.of(TWENTIES);
        }
        if (age < 40) {
            return Optional.of(THIRTIES);
        }
        if (age < 50) {
            return Optional.of(FORTIES);
        }
        if (age < 60) {
            return Optional.of(FIFTIES);
        }
        return Optional.of(SIXTIES_PLUS);
    }
}
