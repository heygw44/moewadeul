package com.moewadeul.trust.application.report;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.LockedMember;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.trust.application.TrustAuditDetails;
import com.moewadeul.trust.application.sanction.SuspensionResyncer;
import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.MemberReportRepository;
import com.moewadeul.trust.domain.report.ReportStatus;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportDismissService {

    private final MemberReportRepository memberReportRepository;
    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final SuspensionResyncer suspensionResyncer;
    private final OutboxEventRecorder outboxEventRecorder;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public MemberReportStatusResult dismiss(long reportId, long adminId, String note) {
        MemberReport report =
                memberReportRepository.findByIdForUpdate(reportId).orElseThrow(BusinessException::notFound);
        report.requireInReview();
        Instant now = clock.instant();
        LockedMember target = memberSuspensionService.lockForSanction(report.getTargetMemberId());
        Long liftedSanctionId = liftTemporarySuspensions(report, target, adminId, now);
        ReportStatus before = report.getStatus();
        report.dismiss(adminId, note, now);
        outboxEventRecorder.recordAll(report.pullEvents());
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.REPORT_DISMISS,
                AdminAuditTargetType.MEMBER_REPORT,
                reportId,
                new TrustAuditDetails.Dismiss(before.name(), report.getStatus().name(), liftedSanctionId));
        log.info("member report dismissed reportId={} adminId={}", reportId, adminId);
        return new MemberReportStatusResult(reportId, report.getStatus().name());
    }

    private Long liftTemporarySuspensions(MemberReport report, LockedMember target, long adminId, Instant now) {
        List<Sanction> temporaries = sanctionRepository.findUnrevokedTemporaryByReportId(report.getId());
        if (temporaries.isEmpty()) {
            return null;
        }
        boolean wasSuspending =
                temporaries.stream().anyMatch(sanction -> sanction.getStatus() == SanctionStatus.ACTIVE);
        temporaries.forEach(sanction -> sanction.liftForDismissedReport(adminId, now));
        if (wasSuspending) {
            suspensionResyncer.resync(target);
        }
        return temporaries.get(0).getId();
    }
}
