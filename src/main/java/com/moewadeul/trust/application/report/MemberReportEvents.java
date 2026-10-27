package com.moewadeul.trust.application.report;

public final class MemberReportEvents {

    public static final String RESOLVED_EVENT_TYPE = "MEMBER_REPORT_RESOLVED";
    public static final String AGGREGATE_TYPE = "MEMBER_REPORT";

    private MemberReportEvents() {}

    public record ResolvedPayload(long reportId, long reporterId, String result) {}
}
