package com.moewadeul.member.application.profile;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.member.domain.Member;

public record MyInfo(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        boolean emailVerified,
        AgeGroup selfAgeGroup,
        Gender selfGender) {

    static MyInfo from(Member member) {
        return new MyInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getStatus().name(),
                member.getRole().name(),
                member.isEmailVerified(),
                member.getSelfAgeGroup(),
                member.getSelfGender());
    }

    @Override
    public String toString() {
        return "MyInfo[memberId=%d, nickname=%s, status=%s, role=%s, emailVerified=%s, selfAgeGroup=%s, selfGender=%s]"
                .formatted(memberId, nickname, status, role, emailVerified, selfAgeGroup, selfGender);
    }
}
