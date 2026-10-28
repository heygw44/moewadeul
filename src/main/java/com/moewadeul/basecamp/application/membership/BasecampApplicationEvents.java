package com.moewadeul.basecamp.application.membership;

public final class BasecampApplicationEvents {

    public static final String EVENT_TYPE = "BASECAMP_APPLIED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampApplicationEvents() {}

    public record Payload(long applicationId, long applicantId, long leaderId) {}
}
