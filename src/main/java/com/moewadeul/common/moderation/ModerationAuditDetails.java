package com.moewadeul.common.moderation;

public final class ModerationAuditDetails {

    private ModerationAuditDetails() {}

    public record Hide(String fromStatus, String toStatus) {}

    public record Restore(String fromStatus, String toStatus, int reviewedReportCount) {}
}
