package com.moewadeul.trust.application;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class TrustAuditDetails {

    private TrustAuditDetails() {}

    public record StartReview(String fromStatus, String toStatus) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(
            String fromStatus,
            String toStatus,
            boolean hideReview,
            Long sanctionId,
            String sanctionType,
            Integer level) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dismiss(String fromStatus, String toStatus, Long liftedSanctionId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lift(long memberId, String sanctionType, Integer level, String fromStatus, String toStatus) {}
}
