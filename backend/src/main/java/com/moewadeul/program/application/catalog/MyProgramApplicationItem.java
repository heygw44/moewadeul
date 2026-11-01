package com.moewadeul.program.application.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramPhase;
import java.time.Instant;

public record MyProgramApplicationItem(
        long applicationId,
        ProgramApplicationStatus status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        ProgramCancelReason cancelReason,
        Instant createdAt,
        Program program) {

    public record Program(
            long programId,
            String title,
            String locationText,
            Instant startAt,
            Instant endAt,
            int fee,
            ProgramPhase status) {}
}
