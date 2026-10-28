package com.moewadeul.spot.application.moderation;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class SpotAuditDetails {

    private SpotAuditDetails() {}

    public record Hide(String spotType, String fromStatus, String toStatus) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Restore(String spotType, String fromStatus, String toStatus, int reviewedReportCount) {}
}
