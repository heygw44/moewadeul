package com.moewadeul.member.application.login;

public record LoginCommand(String email, String password, String ip) {

    @Override
    public String toString() {
        return "LoginCommand[ip=" + ip + "]";
    }
}
