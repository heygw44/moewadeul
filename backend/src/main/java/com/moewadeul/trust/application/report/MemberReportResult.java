package com.moewadeul.trust.application.report;

import java.time.Instant;

public record MemberReportResult(long reportId, long targetMemberId, Instant temporarySuspensionEndsAt) {

    boolean isTargetSuspended() {
        return temporarySuspensionEndsAt != null;
    }
}
