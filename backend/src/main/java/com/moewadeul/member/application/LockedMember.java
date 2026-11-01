package com.moewadeul.member.application;

import com.moewadeul.member.domain.Member;

public final class LockedMember {

    private final Member member;

    LockedMember(Member member) {
        this.member = member;
    }

    public long id() {
        return member.getId();
    }

    Member member() {
        return member;
    }
}
