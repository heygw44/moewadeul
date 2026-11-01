package com.moewadeul.trust.application.profile;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import java.util.List;

public record TrustDetail(
        boolean identityVerified,
        boolean adult,
        TrustLevel trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        int recentEarlyLeaves,
        boolean noRecentSanction,
        List<String> topTags,
        AgeGroup verifiedAgeGroup,
        Gender verifiedGender) {}
