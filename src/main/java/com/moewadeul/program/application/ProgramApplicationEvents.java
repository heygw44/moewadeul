package com.moewadeul.program.application;

public final class ProgramApplicationEvents {

    public static final String CONFIRMED_EVENT_TYPE = "PROGRAM_APPLICATION_CONFIRMED";
    public static final String CANCELED_EVENT_TYPE = "PROGRAM_APPLICATION_CANCELED";
    public static final String EXPIRED_EVENT_TYPE = "PROGRAM_APPLICATION_EXPIRED";
    public static final String AGGREGATE_TYPE = "PROGRAM_APPLICATION";

    private ProgramApplicationEvents() {}

    public record ConfirmedPayload(long applicationId, long memberId, long programId) {}

    public record CanceledPayload(long applicationId, long memberId, long programId, String reason, boolean refunded) {}

    public record ExpiredPayload(long applicationId, long memberId, long programId) {}
}
