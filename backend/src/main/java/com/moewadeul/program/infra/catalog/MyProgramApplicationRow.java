package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramStatus;
import java.time.Instant;

public record MyProgramApplicationRow(
        long applicationId,
        ProgramApplicationStatus status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        ProgramCancelReason cancelReason,
        Instant createdAt,
        long programId,
        String title,
        String locationText,
        Instant startAt,
        Instant endAt,
        int fee,
        ProgramStatus programStatus,
        Instant applyOpenAt,
        Instant applyCloseAt) {}
