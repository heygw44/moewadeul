package com.moewadeul.basecamp.application.lifecycle;

import java.util.List;

public final class BasecampTransitionEvents {

    public static final String CONFIRMED_EVENT_TYPE = "BASECAMP_CONFIRMED";
    public static final String CANCELED_EVENT_TYPE = "BASECAMP_CANCELED";
    public static final String COMPLETED_EVENT_TYPE = "BASECAMP_COMPLETED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampTransitionEvents() {}

    public record Payload(long basecampId, List<Long> memberIds) {}
}
