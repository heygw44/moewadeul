package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.domain.BasecampRelation;
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
        String status,
        JoinConditionSummary joinCondition,
        DetailMember leader,
        List<DetailMember> members,
        BasecampRelation myRelation,
        String contactInfo,
        JoinEligibility eligibility) {

    public record SpotSummary(long spotId, String name, String type, double lat, double lng) {}

    public record DetailMember(
            long memberId,
            String nickname,
            String role,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
