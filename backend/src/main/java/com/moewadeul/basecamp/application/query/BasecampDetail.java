package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.domain.BasecampMemberRole;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import java.time.LocalDate;
import java.util.List;

public record BasecampDetail(
        long basecampId,
        String title,
        String description,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        BasecampStatus status,
        JoinConditionSummary joinCondition,
        DetailMember leader,
        List<DetailMember> members,
        BasecampRelation myRelation,
        String contactInfo,
        JoinEligibility eligibility) {

    public record SpotSummary(long spotId, String name, BasecampSpotType type, double lat, double lng) {}

    public record DetailMember(
            long memberId,
            String nickname,
            BasecampMemberRole role,
            AgeGroup ageGroup,
            boolean ageGroupVerified,
            Gender gender,
            boolean genderVerified,
            TrustLevel trustLevel,
            int completedCompanions) {}
}
