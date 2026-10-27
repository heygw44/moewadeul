package com.moewadeul.trust.api.profile;

import com.moewadeul.trust.application.profile.MemberTrustProfile;
import java.util.List;

public sealed interface MemberProfileResponse {

    record Anonymous(long memberId, String nickname) implements MemberProfileResponse {}

    record Member(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions,
            CompanionReviewSummary companionReviewSummary)
            implements MemberProfileResponse {

        static Member from(MemberTrustProfile profile) {
            return new Member(
                    profile.memberId(),
                    profile.nickname(),
                    profile.ageGroup(),
                    profile.ageGroupVerified(),
                    profile.gender(),
                    profile.genderVerified(),
                    profile.trustLevel(),
                    profile.completedCompanions(),
                    new CompanionReviewSummary(profile.rejoinRate(), profile.topTags()));
        }
    }

    record CompanionReviewSummary(Integer rejoinRate, List<String> topTags) {}
}
