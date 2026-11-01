package com.moewadeul.common.event;

import java.util.List;

public final class BasecampEvents {

    public static final String AGGREGATE_TYPE = "BASECAMP";

    public static final String APPLIED = "BASECAMP_APPLIED";
    public static final String APPROVED = "BASECAMP_APPROVED";
    public static final String REJECTED = "BASECAMP_REJECTED";
    public static final String KICKED = "BASECAMP_KICKED";
    public static final String MEMBER_CHANGED = "BASECAMP_MEMBER_CHANGED";
    public static final String CONFIRMED = "BASECAMP_CONFIRMED";
    public static final String CANCELED = "BASECAMP_CANCELED";
    public static final String COMPLETED = "BASECAMP_COMPLETED";

    private BasecampEvents() {}

    public record AppliedPayload(long applicationId, long applicantId, long leaderId) {}

    public record DecisionPayload(long applicationId, long applicantId, long basecampId) {}

    public record KickedPayload(long basecampId, long memberId, String reason) {}

    public record MembersPayload(long basecampId, List<Long> memberIds) {}
}
