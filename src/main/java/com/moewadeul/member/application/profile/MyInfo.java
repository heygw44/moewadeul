package com.moewadeul.member.application.profile;

import com.moewadeul.member.domain.Member;

public record MyInfo(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        String selfAgeGroup,
        String selfGender) {

    static MyInfo from(Member member) {
        return new MyInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getStatus().name(),
                member.getRole().name(),
                nameOrNull(member.getSelfAgeGroup()),
                nameOrNull(member.getSelfGender()));
    }

    private static String nameOrNull(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value.name();
    }

    @Override
    public String toString() {
        return "MyInfo[memberId=%d, nickname=%s, status=%s, role=%s, selfAgeGroup=%s, selfGender=%s]"
                .formatted(memberId, nickname, status, role, selfAgeGroup, selfGender);
    }
}
