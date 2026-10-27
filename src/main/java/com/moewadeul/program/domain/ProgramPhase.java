package com.moewadeul.program.domain;

import java.time.Instant;

public enum ProgramPhase {
    UPCOMING,
    OPEN,
    CLOSED,
    CANCELED;

    public static ProgramPhase of(ProgramStatus status, Instant applyOpenAt, Instant applyCloseAt, Instant now) {
        if (status == ProgramStatus.CANCELED) {
            return CANCELED;
        }
        if (now.isBefore(applyOpenAt)) {
            return UPCOMING;
        }
        if (now.isBefore(applyCloseAt)) {
            return OPEN;
        }
        return CLOSED;
    }
}
