package com.moewadeul.trust.application.report;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.application.TrustAuditDetails;
import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.MemberReportRepository;
import com.moewadeul.trust.domain.report.ReportStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportReviewService {

    private final MemberReportRepository memberReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public MemberReportStatusResult startReview(long reportId, long adminId) {
        MemberReport report =
                memberReportRepository.findByIdForUpdate(reportId).orElseThrow(BusinessException::notFound);
        ReportStatus before = report.getStatus();
        report.startReview(adminId, clock.instant());
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.REPORT_START_REVIEW,
                AdminAuditTargetType.MEMBER_REPORT,
                reportId,
                new TrustAuditDetails.StartReview(
                        before.name(), report.getStatus().name()));
        log.info("member report review started reportId={} adminId={}", reportId, adminId);
        return new MemberReportStatusResult(reportId, report.getStatus().name());
    }
}
