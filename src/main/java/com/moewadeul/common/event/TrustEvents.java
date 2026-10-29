package com.moewadeul.common.event;

import java.time.Instant;

public final class TrustEvents {

    public static final String MEMBER_AGGREGATE_TYPE = "MEMBER";
    public static final String REPORT_AGGREGATE_TYPE = "MEMBER_REPORT";

    public static final String REPORT_RESOLVED = "MEMBER_REPORT_RESOLVED";
    public static final String SANCTION_CONFIRMED = "SANCTION_CONFIRMED";
    public static final String SANCTION_BASECAMP_CLEANUP = "SANCTION_BASECAMP_CLEANUP";
    public static final String SANCTION_PROGRAM_CLEANUP = "SANCTION_PROGRAM_CLEANUP";
    public static final String WITHDRAWAL_BASECAMP_CLEANUP = "WITHDRAWAL_BASECAMP_CLEANUP";
    public static final String WITHDRAWAL_PROGRAM_CLEANUP = "WITHDRAWAL_PROGRAM_CLEANUP";
    public static final String WITHDRAWAL_MEMORY_CLEANUP = "WITHDRAWAL_MEMORY_CLEANUP";

    private TrustEvents() {}

    public record ReportResolvedPayload(long reportId, long reporterId, String result) {}

    public record SanctionConfirmedPayload(long memberId, long sanctionId, String sanctionType, Instant endsAt) {}

    public record SanctionPayload(long memberId, long sanctionId) {}

    public record WithdrawalPayload(long memberId) {}
}
