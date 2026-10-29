package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import java.time.Instant;

public record BasecampApplicationItem(
        long applicationId, BasecampApplicationStatus status, String message, Instant appliedAt, Applicant applicant) {

    public record Applicant(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
