package com.moewadeul.common.event;

import java.util.List;

public final class ProgramEvents {

    public static final String APPLICATION_AGGREGATE_TYPE = "PROGRAM_APPLICATION";
    public static final String PROGRAM_AGGREGATE_TYPE = "PROGRAM";

    public static final String APPLICATION_CONFIRMED = "PROGRAM_APPLICATION_CONFIRMED";
    public static final String APPLICATION_CANCELED = "PROGRAM_APPLICATION_CANCELED";
    public static final String APPLICATION_EXPIRED = "PROGRAM_APPLICATION_EXPIRED";
    public static final String SEAT_RELEASED = "PROGRAM_SEAT_RELEASED";

    private ProgramEvents() {}

    public record ApplicationPayload(long applicationId, long memberId, long programId) {}

    public record CanceledPayload(long applicationId, long memberId, long programId, String reason, boolean refunded) {}

    public record SeatReleasedPayload(long programId, List<Long> memberIds) {}
}
