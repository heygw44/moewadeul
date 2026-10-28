package com.moewadeul.member.domain;

public record Nickname(String value) {

    public Nickname {
        Member.requireValidNickname(value);
    }
}
