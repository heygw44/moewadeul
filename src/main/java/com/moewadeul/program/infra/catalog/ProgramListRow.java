package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramStatus;
import java.time.Instant;

public record ProgramListRow(
        long programId,
        String title,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        boolean overnight,
        ProgramStatus status) {}
