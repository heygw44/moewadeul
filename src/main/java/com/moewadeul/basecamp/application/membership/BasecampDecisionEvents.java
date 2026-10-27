package com.moewadeul.basecamp.application.membership;

public final class BasecampDecisionEvents {

    public static final String APPROVED_EVENT_TYPE = "BASECAMP_APPROVED";
    public static final String REJECTED_EVENT_TYPE = "BASECAMP_REJECTED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampDecisionEvents() {}

    public record Payload(long applicationId, long applicantId, long basecampId) {}
}
