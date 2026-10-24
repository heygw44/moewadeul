package com.moewadeul.trust.application.report;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
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

/** 관리자가 신고를 기각한다. 처리 메모는 로그에 남기지 않는다. */
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

    /**
     * 호출하면 검토 중인 신고를 기각한다. 그 신고가 만든 임시 정지를 해제하고, 적용 중인 것이 있었으면 회원의 정지 상태를 남은 정지에 맞춰 다시 정한다.
     * 72시간이 지나 이미 만료된 임시 정지도 해제한다. 기각된 신고의 임시 정지는 제재 이력에 남기지 않기 때문이다.
     * 같은 회원에게 다른 임시 정지나 확정 정지가 남아 있으면 회원은 정지 상태로 남는다. 세션은 지우지 않는다. 풀리는 쪽이라 막을 것이 없다.
     *
     * <p>신고 행, 회원 행, 제재 행 순서로 잠근다. 신고가 없으면 NOT_FOUND, 검토 중이 아니면 REPORT_INVALID_STATE로 거부한다.
     */
    @Transactional
    public MemberReportStatusResult dismiss(long reportId, long adminId, String note) {
        MemberReport report = memberReportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        report.requireInReview();
        Instant now = clock.instant();
        memberSuspensionService.lockForSanction(report.getTargetMemberId());
        Long liftedSanctionId = liftTemporarySuspensions(report, adminId, now);
        ReportStatus before = report.getStatus();
        report.dismiss(adminId, note, now);
        outboxEventRecorder.record(
                MemberReportEvents.RESOLVED_EVENT_TYPE,
                MemberReportEvents.AGGREGATE_TYPE,
                reportId,
                new MemberReportEvents.ResolvedPayload(
                        reportId, report.getReporterId(), ReportStatus.DISMISSED.name()));
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.REPORT_DISMISS,
                AdminAuditTargetType.MEMBER_REPORT,
                reportId,
                new TrustAuditDetails.Dismiss(before.name(), report.getStatus().name(), liftedSanctionId));
        log.info("member report dismissed reportId={} adminId={}", reportId, adminId);
        return new MemberReportStatusResult(reportId, report.getStatus().name());
    }

    // 임시 정지가 없으면(긴급 신고가 아니거나, 긴급 신고라도 신고자나 대상이 신청만 했으면) 바꿀 것이 없다.
    // 이미 만료된 임시 정지는 회원의 정지에 영향이 없으므로, 적용 중인 것을 풀었을 때만 회원의 정지 상태를 다시 계산한다.
    private Long liftTemporarySuspensions(MemberReport report, long adminId, Instant now) {
        List<Sanction> temporaries = sanctionRepository.findUnliftedTemporaryByReportId(report.getId());
        if (temporaries.isEmpty()) {
            return null;
        }
        boolean wasSuspending =
                temporaries.stream().anyMatch(sanction -> sanction.getStatus() == SanctionStatus.ACTIVE);
        temporaries.forEach(sanction -> sanction.liftForDismissedReport(adminId, now));
        if (wasSuspending) {
            suspensionResyncer.resync(report.getTargetMemberId());
        }
        return temporaries.get(0).getId();
    }
}
