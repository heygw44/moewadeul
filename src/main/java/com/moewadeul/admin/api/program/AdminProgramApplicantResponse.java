package com.moewadeul.admin.api.program;

import com.moewadeul.program.application.catalog.ProgramApplicantItem;
import com.moewadeul.program.application.catalog.ProgramApplicantPage;
import java.time.Instant;
import java.util.List;

public record AdminProgramApplicantPageResponse(List<ApplicantResponse> content, int page, int size, boolean hasNext) {

    static AdminProgramApplicantPageResponse from(ProgramApplicantPage page) {
        List<ApplicantResponse> content =
                page.content().stream().map(ApplicantResponse::from).toList();
        return new AdminProgramApplicantPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record ApplicantResponse(
            long applicationId,
            MemberResponse member,
            String status,
            Instant paymentDueAt,
            Instant confirmedAt,
            Instant canceledAt,
            String cancelReason,
            Instant reviewRequestedAt,
            Instant createdAt) {

        static ApplicantResponse from(ProgramApplicantItem item) {
            return new ApplicantResponse(
                    item.applicationId(),
                    new MemberResponse(item.member().memberId(), item.member().nickname()),
                    item.status(),
                    item.paymentDueAt(),
                    item.confirmedAt(),
                    item.canceledAt(),
                    item.cancelReason(),
                    item.reviewRequestedAt(),
                    item.createdAt());
        }
    }

    public record MemberResponse(long memberId, String nickname) {}
}
