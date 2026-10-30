package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionCommand;
import com.moewadeul.trust.application.report.MemberReportActionResult;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.domain.report.ReportType;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Clock;

public final class SanctionFixture {

    private final CompanionReviewFixture companionFixture;
    private final MemberReportFixture reportFixture;
    private final MemberReportActionService memberReportActionService;
    private final Clock clock;
    private long basecampId;

    public SanctionFixture(
            CompanionReviewFixture companionFixture,
            MemberReportFixture reportFixture,
            MemberReportActionService memberReportActionService,
            Clock clock) {
        this.companionFixture = companionFixture;
        this.reportFixture = reportFixture;
        this.memberReportActionService = memberReportActionService;
        this.clock = clock;
    }

    public long receiveReport(long targetId) {
        if (basecampId == 0) {
            basecampId = companionFixture.saveBasecamp("COMPLETED", clock.instant());
        }
        long reporterId = companionFixture.saveVerifiedMember(TestSequence.nickname());
        return reportFixture.insertMemberReport(
                reporterId, targetId, basecampId, ReportType.NO_SHOW.name(), "IN_REVIEW", clock.instant());
    }

    public MemberReportActionResult confirm(long targetId, long adminId, SanctionType type, String reason) {
        return confirmReport(receiveReport(targetId), adminId, type, reason);
    }

    public MemberReportActionResult confirmReport(long reportId, long adminId, SanctionType type, String reason) {
        return memberReportActionService.act(
                new MemberReportActionCommand(reportId, adminId, type.name(), reason, false, null));
    }
}
