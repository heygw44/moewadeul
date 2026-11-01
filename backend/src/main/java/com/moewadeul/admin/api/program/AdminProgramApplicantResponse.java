package com.moewadeul.admin.api.program;

import com.moewadeul.program.application.catalog.ProgramApplicantItem;
import java.time.Instant;

public record AdminProgramApplicantResponse(
        long applicationId,
        MemberResponse member,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant reviewRequestedAt,
        Instant createdAt) {

    static AdminProgramApplicantResponse from(ProgramApplicantItem item) {
        return new AdminProgramApplicantResponse(
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

    public record MemberResponse(long memberId, String nickname) {}
}
