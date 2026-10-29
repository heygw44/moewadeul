package com.moewadeul.trust.api.profile;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.member.application.profile.MyInfo;
import com.moewadeul.trust.application.profile.TrustSummary;

public record MeResponse(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        AgeGroup selfAgeGroup,
        Gender selfGender,
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
                trustSummary.trustLevel().level());
    }

    @Override
    public String toString() {
        return "MeResponse[memberId=" + memberId + ", nickname=" + nickname + ", status=" + status + ", role=" + role
                + ", selfAgeGroup=" + selfAgeGroup + ", selfGender=" + selfGender + ", identityVerified="
                + identityVerified + ", trustLevel=" + trustLevel + "]";
    }
}
