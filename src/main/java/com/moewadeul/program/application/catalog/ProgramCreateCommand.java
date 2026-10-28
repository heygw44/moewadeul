package com.moewadeul.program.application.catalog;

import java.time.Instant;

public record ProgramCreateCommand(
        String title,
        String description,
        Long spotId,
        String locationText,
        Instant startAt,
        Instant endAt,
        int capacity,
        int fee,
        Instant applyOpenAt,
        Instant applyCloseAt,
        Integer paymentDeadlineMinutes,
        boolean overnight) {}
