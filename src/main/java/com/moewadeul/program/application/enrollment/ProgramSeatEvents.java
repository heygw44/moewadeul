package com.moewadeul.program.application.enrollment;

import java.util.List;

public final class ProgramSeatEvents {

    public static final String SEAT_RELEASED_EVENT_TYPE = "PROGRAM_SEAT_RELEASED";
    public static final String AGGREGATE_TYPE = "PROGRAM";

    private ProgramSeatEvents() {}

    public record SeatReleasedPayload(long programId, List<Long> memberIds) {}
}
