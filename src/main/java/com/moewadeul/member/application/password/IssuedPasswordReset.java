package com.moewadeul.member.application.password;

public record IssuedPasswordReset(String email, String token) {

    @Override
    public String toString() {
        return "IssuedPasswordReset[****]";
    }
}
