package com.moewadeul.program.application.catalog;

import com.moewadeul.program.domain.ProgramPhase;
import java.time.Instant;

public record ProgramSummary(
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
        ProgramPhase status) {}
