package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplicationItem;
import com.moewadeul.basecamp.application.membership.BasecampApplicationPage;
import java.time.Instant;
import java.util.List;

public record BasecampApplicationPageResponse(List<ApplicationEntry> content, int page, int size, boolean hasNext) {

    static BasecampApplicationPageResponse from(BasecampApplicationPage page) {
        List<ApplicationEntry> content =
                page.content().stream().map(ApplicationEntry::from).toList();
        return new BasecampApplicationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record ApplicationEntry(
            long applicationId, String status, String message, Instant appliedAt, ApplicantEntry applicant) {

        static ApplicationEntry from(BasecampApplicationItem item) {
            BasecampApplicationItem.Applicant applicant = item.applicant();
            return new ApplicationEntry(
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
                            applicant.trustLevel(),
                            applicant.completedCompanions()));
        }
    }

    public record ApplicantEntry(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
