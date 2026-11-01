package com.moewadeul.trust.application.identity;

import com.moewadeul.common.profile.Gender;

public record IdentityVerifyCommand(int birthYear, Gender gender, String demoIdentityKey) {

    @Override
    public String toString() {
        return "IdentityVerifyCommand[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
