package com.moewadeul.trust.domain.identity;

import com.moewadeul.common.profile.Gender;

public record VerifiedIdentity(String ci, int birthYear, Gender gender) {

    @Override
    public String toString() {
        return "VerifiedIdentity[ci=****, birthYear=****, gender=" + gender + "]";
    }
}
