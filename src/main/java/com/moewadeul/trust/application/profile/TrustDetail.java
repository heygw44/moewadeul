package com.moewadeul.trust.application.profile;

import java.util.List;

public record TrustDetail(
        boolean identityVerified,
        boolean adult,
        int trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        int recentEarlyLeaves,
        boolean noRecentSanction,
        List<String> topTags,
        String verifiedAgeGroup,
        String verifiedGender) {}
