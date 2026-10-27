package com.moewadeul.basecamp.application.membership;

import java.time.Instant;

public record BasecampApplicationItem(
        long applicationId, String status, String message, Instant appliedAt, Applicant applicant) {

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
