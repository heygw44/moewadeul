package com.moewadeul.trust.infra.report;

import java.time.Instant;

public record MemberReportContextRow(
        boolean reporterInvolved,
        boolean targetInvolved,
        boolean reporterMember,
        boolean targetMember,
        boolean alreadyReported,
        boolean reviewMatches,
        boolean reverseWritten,
        Instant completedAt) {

    public boolean bothMembers() {
        return reporterMember && targetMember;
    }
}
