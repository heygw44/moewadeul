package com.moewadeul.trust.api.profile;

import com.moewadeul.member.application.profile.MyInfo;
import com.moewadeul.trust.application.profile.TrustSummary;

public record MeResponse(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        String selfAgeGroup,
        String selfGender,
        boolean identityVerified,
        int trustLevel) {

    public static MeResponse of(MyInfo myInfo, TrustSummary trustSummary) {
        return new MeResponse(
                myInfo.memberId(),
                myInfo.email(),
                myInfo.nickname(),
                myInfo.status(),
                myInfo.role(),
                myInfo.selfAgeGroup(),
                myInfo.selfGender(),
                trustSummary.identityVerified(),
                trustSummary.trustLevel());
    }

    @Override
    public String toString() {
        return "MeResponse[memberId=" + memberId + ", nickname=" + nickname + ", status=" + status + ", role=" + role
                + ", selfAgeGroup=" + selfAgeGroup + ", selfGender=" + selfGender + ", identityVerified="
                + identityVerified + ", trustLevel=" + trustLevel + "]";
    }
}
