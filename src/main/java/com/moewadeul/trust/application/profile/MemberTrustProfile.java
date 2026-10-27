package com.moewadeul.trust.application.profile;

import java.util.List;

public record MemberTrustProfile(
        long memberId,
        String nickname,
        String ageGroup,
        boolean ageGroupVerified,
        String gender,
        boolean genderVerified,
        int trustLevel,
        int completedCompanions,
        Integer rejoinRate,
        List<String> topTags) {}
