package com.moewadeul.program.application.catalog;

import java.time.Instant;

public record MyProgramApplicationItem(
        long applicationId,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant createdAt,
        Program program) {

    public record Program(
            long programId,
            String title,
            String locationText,
            Instant startAt,
            Instant endAt,
            int fee,
            String status) {}
}
