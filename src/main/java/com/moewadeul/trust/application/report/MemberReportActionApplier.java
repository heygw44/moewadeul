package com.moewadeul.trust.application.report;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.trust.application.TrustAuditDetails;
import com.moewadeul.trust.application.sanction.SanctionConfirmApplier;
import com.moewadeul.trust.application.sanction.SanctionConfirmCommand;
import com.moewadeul.trust.application.sanction.SanctionConfirmResult;
import com.moewadeul.trust.domain.companion.CompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewRepository;
import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.MemberReportRepository;
import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportStatus;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberReportActionApplier {

    private final MemberReportRepository memberReportRepository;
    private final CompanionReviewRepository companionReviewRepository;
    private final SanctionRepository sanctionRepository;
    private final SanctionConfirmApplier sanctionConfirmApplier;
    private final OutboxEventRecorder outboxEventRecorder;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public MemberReportActionResult apply(MemberReportActionCommand command, SanctionType sanctionType) {
        MemberReport report = memberReportRepository
                .findByIdForUpdate(command.reportId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        report.requireInReview();
        requireConsistentRequest(report, command, sanctionType);
        Instant now = clock.instant();
        SanctionConfirmResult confirmed = sanctionType == null ? null : confirmSanction(report, command, sanctionType);
        if (command.hideReview()) {
            hideReview(report, now);
        }
        ReportStatus before = report.getStatus();
        report.action(command.adminId(), command.note(), now);
        outboxEventRecorder.record(
                MemberReportEvents.RESOLVED_EVENT_TYPE,
                MemberReportEvents.AGGREGATE_TYPE,
                report.getId(),
                new MemberReportEvents.ResolvedPayload(
                        report.getId(), report.getReporterId(), ReportStatus.ACTIONED.name()));
        Long sanctionId = confirmed == null ? null : confirmed.sanctionId();
        recordAudit(report, before, command, sanctionId);
        return new MemberReportActionResult(
                report.getId(),
                report.getStatus().name(),
                sanctionId,
                report.getTargetMemberId(),
                confirmed != null && confirmed.suspended());
    }

    private static void requireConsistentRequest(
            MemberReport report, MemberReportActionCommand command, SanctionType sanctionType) {
        if (sanctionType == null && !command.hideReview()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "제재를 내리거나 후기를 숨기는 요청이 하나는 있어야 해요.");
        }
        if (command.hideReview() && report.getKind() != ReportKind.REVIEW) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "후기 신고만 후기를 숨길 수 있어요.");
        }
    }

    private SanctionConfirmResult confirmSanction(
            MemberReport report, MemberReportActionCommand command, SanctionType sanctionType) {
        return sanctionConfirmApplier.apply(new SanctionConfirmCommand(
                report.getTargetMemberId(), report.getId(), sanctionType, command.sanctionReason(), command.adminId()));
    }

    private void hideReview(MemberReport report, Instant now) {
        CompanionReview review = companionReviewRepository
                .findById(report.getCompanionReviewId())
                .orElseThrow(() -> new IllegalStateException("신고된 동행 후기가 없습니다. reportId=" + report.getId()));
        review.hide(now);
    }

    private void recordAudit(
            MemberReport report, ReportStatus before, MemberReportActionCommand command, Long sanctionId) {
        Sanction sanction = sanctionId == null
                ? null
                : sanctionRepository
                        .findById(sanctionId)
                        .orElseThrow(() -> new IllegalStateException("방금 확정한 제재가 없습니다. sanctionId=" + sanctionId));
        Integer level = sanction == null || sanction.getLevel() == null
                ? null
                : sanction.getLevel().intValue();
        adminAuditRecorder.record(
                command.adminId(),
                AdminAuditAction.REPORT_ACTION,
                AdminAuditTargetType.MEMBER_REPORT,
                report.getId(),
                new TrustAuditDetails.Action(
                        before.name(),
                        report.getStatus().name(),
                        command.hideReview(),
                        sanctionId,
                        sanction == null ? null : sanction.getType().name(),
                        level));
    }
}
