package com.moewadeul.member.application.profile;

import com.moewadeul.member.domain.Member;

public record MemberProfile(long memberId, String nickname, String selfAgeGroup, String selfGender) {

    static MemberProfile from(Member member) {
        return new MemberProfile(
                member.getId(),
                member.getNickname(),
                nameOrNull(member.getSelfAgeGroup()),
                nameOrNull(member.getSelfGender()));
    }

    private static String nameOrNull(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value.name();
    }
}
