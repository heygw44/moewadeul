package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramStatus;
import java.time.Instant;

public record ProgramDetailRow(
        long programId,
        String title,
        String description,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        int paymentDeadlineMinutes,
        boolean overnight,
        ProgramStatus status) {}
