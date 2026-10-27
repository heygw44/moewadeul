package com.moewadeul.trust.application.sanction;

import java.time.Instant;

public final class SanctionEvents {

    public static final String BASECAMP_CLEANUP_EVENT_TYPE = "SANCTION_BASECAMP_CLEANUP";
    public static final String PROGRAM_CLEANUP_EVENT_TYPE = "SANCTION_PROGRAM_CLEANUP";
    public static final String NOTIFICATION_EVENT_TYPE = "SANCTION_CONFIRMED";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private SanctionEvents() {}

    public record Payload(long memberId, long sanctionId) {}

    public record NotificationPayload(long memberId, long sanctionId, String sanctionType, Instant endsAt) {}
}
