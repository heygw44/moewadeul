package com.moewadeul.trust.domain.identity;

public record IdentityClaim(int birthYear, Gender gender, String demoIdentityKey) {

    @Override
    public String toString() {
        return "IdentityClaim[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
