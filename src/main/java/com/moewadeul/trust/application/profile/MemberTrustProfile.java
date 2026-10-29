package com.moewadeul.trust.application.profile;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import java.util.List;

public record MemberTrustProfile(
        long memberId,
        String nickname,
        AgeGroup ageGroup,
        boolean ageGroupVerified,
        Gender gender,
        boolean genderVerified,
        TrustLevel trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        List<String> topTags) {}
