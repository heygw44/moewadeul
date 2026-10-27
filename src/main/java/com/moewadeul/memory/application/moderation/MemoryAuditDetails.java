package com.moewadeul.memory.application.moderation;

public final class MemoryAuditDetails {

    private MemoryAuditDetails() {}

    public record Hide(String fromStatus, String toStatus) {}

    public record Restore(String fromStatus, String toStatus, int reviewedReportCount) {}
}
