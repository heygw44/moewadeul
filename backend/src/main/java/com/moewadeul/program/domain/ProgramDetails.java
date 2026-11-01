package com.moewadeul.program.domain;

import java.time.Instant;

public record ProgramDetails(
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
        int paymentDeadlineMinutes,
        boolean overnight) {}
