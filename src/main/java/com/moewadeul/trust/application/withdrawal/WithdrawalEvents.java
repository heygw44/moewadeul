package com.moewadeul.trust.application.withdrawal;

public final class WithdrawalEvents {

    public static final String BASECAMP_CLEANUP_EVENT_TYPE = "WITHDRAWAL_BASECAMP_CLEANUP";
    public static final String PROGRAM_CLEANUP_EVENT_TYPE = "WITHDRAWAL_PROGRAM_CLEANUP";
    public static final String MEMORY_CLEANUP_EVENT_TYPE = "WITHDRAWAL_MEMORY_CLEANUP";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private WithdrawalEvents() {}

    public record Payload(long memberId) {}
}
