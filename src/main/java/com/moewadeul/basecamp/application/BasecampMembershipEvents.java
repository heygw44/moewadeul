package com.moewadeul.basecamp.application;

import java.util.List;

public final class BasecampMembershipEvents {

    public static final String KICKED_EVENT_TYPE = "BASECAMP_KICKED";
    public static final String MEMBER_CHANGED_EVENT_TYPE = "BASECAMP_MEMBER_CHANGED";
    public static final String AGGREGATE_TYPE = "BASECAMP";

    private BasecampMembershipEvents() {}

    public record KickedPayload(long basecampId, long memberId, String reason) {}

    public record MemberChangedPayload(long basecampId, List<Long> memberIds) {}
}
