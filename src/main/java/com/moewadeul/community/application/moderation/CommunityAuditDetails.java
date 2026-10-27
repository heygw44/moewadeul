package com.moewadeul.community.application.moderation;

public final class CommunityAuditDetails {

    private CommunityAuditDetails() {}

    public record Hide(String fromStatus, String toStatus) {}

    public record Restore(String fromStatus, String toStatus, int reviewedReportCount) {}
}
