package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import java.time.Instant;

public record BasecampApplicationItem(
        long applicationId, BasecampApplicationStatus status, String message, Instant appliedAt, Applicant applicant) {

    public record Applicant(
            long memberId,
            String nickname,
            AgeGroup ageGroup,
            boolean ageGroupVerified,
            Gender gender,
            boolean genderVerified,
            TrustLevel trustLevel,
            int completedCompanions) {}
}
