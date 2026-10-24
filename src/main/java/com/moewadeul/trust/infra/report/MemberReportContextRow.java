package com.moewadeul.trust.infra.report;

import java.time.Instant;

/**
 * 신고 자격을 가릴 때 읽은 값. completedAt은 베이스캠프가 완료되기 전이면 null이다.
 * reporterMember와 targetMember는 그 베이스캠프의 멤버였던 적이 있는지(상태 무관, 캠프 리더 포함)이고, 신청만 했으면 false다.
 * reviewMatches와 reverseWritten은 후기 신고가 아니면 의미가 없다.
 */
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
