package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import java.time.Instant;

public record ProgramApplicantRow(
        long applicationId,
        long memberId,
        String nickname,
        ProgramApplicationStatus status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        ProgramCancelReason cancelReason,
        Instant reviewRequestedAt,
        Instant createdAt) {}
