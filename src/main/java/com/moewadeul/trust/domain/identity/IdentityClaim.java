package com.moewadeul.trust.domain.identity;

import com.moewadeul.common.profile.Gender;

public record IdentityClaim(int birthYear, Gender gender, String demoIdentityKey) {

    @Override
    public String toString() {
        return "IdentityClaim[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
