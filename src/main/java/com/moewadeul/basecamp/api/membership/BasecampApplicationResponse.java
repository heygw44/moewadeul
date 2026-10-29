package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplicationItem;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import java.time.Instant;

public record BasecampApplicationResponse(
        long applicationId,
        BasecampApplicationStatus status,
        String message,
        Instant appliedAt,
        ApplicantEntry applicant) {

    static BasecampApplicationResponse from(BasecampApplicationItem item) {
        BasecampApplicationItem.Applicant applicant = item.applicant();
        return new BasecampApplicationResponse(
                item.applicationId(),
                item.status(),
                item.message(),
                item.appliedAt(),
                new ApplicantEntry(
                        applicant.memberId(),
                        applicant.nickname(),
                        applicant.ageGroup(),
                        applicant.ageGroupVerified(),
                        applicant.gender(),
                        applicant.genderVerified(),
                        applicant.trustLevel().level(),
                        applicant.completedCompanions()));
    }

    public record ApplicantEntry(
            long memberId,
            String nickname,
            AgeGroup ageGroup,
            boolean ageGroupVerified,
            Gender gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
