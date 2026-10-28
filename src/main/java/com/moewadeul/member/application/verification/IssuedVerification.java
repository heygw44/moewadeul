package com.moewadeul.member.application.verification;

public record IssuedVerification(String email, String code) {

    @Override
    public String toString() {
        return "IssuedVerification[****]";
    }
}
