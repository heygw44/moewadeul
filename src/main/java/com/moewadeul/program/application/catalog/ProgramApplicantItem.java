package com.moewadeul.program.application.catalog;

import java.time.Instant;

public record ProgramApplicantItem(
        long applicationId,
        Applicant member,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant reviewRequestedAt,
        Instant createdAt) {

    public record Applicant(long memberId, String nickname) {}
}
