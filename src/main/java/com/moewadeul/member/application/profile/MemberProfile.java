package com.moewadeul.member.application.profile;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.member.domain.Member;

public record MemberProfile(long memberId, String nickname, AgeGroup selfAgeGroup, Gender selfGender) {

    static MemberProfile from(Member member) {
        return new MemberProfile(
                member.getId(), member.getNickname(), member.getSelfAgeGroup(), member.getSelfGender());
    }
}
