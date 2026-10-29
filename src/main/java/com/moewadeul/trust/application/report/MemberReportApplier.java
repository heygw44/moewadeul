package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.member.application.LockedMember;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.trust.domain.CompanionReviewPolicy;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.MemberReportRepository;
import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.infra.report.MemberReportContextRow;
import com.moewadeul.trust.infra.report.MemberReportMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class MemberReportApplier {

    private final MemberReportRepository memberReportRepository;
    private final SanctionRepository sanctionRepository;
    private final MemberReportMapper memberReportMapper;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    @Transactional
    MemberReportResult apply(long reporterId, MemberReportCommand command) {
        requireConsistentKind(command);
        if (reporterId == command.targetMemberId()) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
        MemberReportContextRow context = memberReportMapper
                .selectContext(
                        command.basecampId(),
                        reporterId,
                        command.targetMemberId(),
                        command.kind().name(),
                        command.companionReviewId())
                .orElseThrow(() -> new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE));
        Instant now = clock.instant();
        requireEligible(context, command, now);
        if (context.alreadyReported()) {
            throw new BusinessException(TrustErrorCode.REPORT_DUPLICATED);
        }
        Instant suspensionEndsAt = command.type().isUrgent() && context.bothMembers()
                ? suspendTarget(reporterId, command.targetMemberId(), now)
                : null;
        MemberReport report = save(MemberReport.receive(
                reporterId,
                command.targetMemberId(),
                command.basecampId(),
                command.companionReviewId(),
                command.type(),
                command.content(),
                now));
        if (suspensionEndsAt != null) {
            sanctionRepository.save(Sanction.temporary(report.getTargetMemberId(), report.getId(), now));
        }
        return new MemberReportResult(report.getId(), report.getTargetMemberId(), suspensionEndsAt);
    }

    private static void requireConsistentKind(MemberReportCommand command) {
        if (command.kind() != command.type().kind()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "신고 종류와 유형이 맞지 않아요.");
        }
        boolean isReviewReport = command.kind() == ReportKind.REVIEW;
        if (isReviewReport && command.companionReviewId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "후기를 신고하려면 동행 후기 ID가 필요해요.");
        }
        if (!isReviewReport && command.companionReviewId() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "회원 신고에는 동행 후기 ID를 넣을 수 없어요.");
        }
    }

    private static void requireEligible(MemberReportContextRow context, MemberReportCommand command, Instant now) {
        if (!context.reporterInvolved() || !context.targetInvolved()) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
        if (command.kind() == ReportKind.REVIEW && !isReportableReview(context, now)) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
    }

    private static boolean isReportableReview(MemberReportContextRow context, Instant now) {
        return context.reviewMatches()
                && context.completedAt() != null
                && CompanionReviewPolicy.isRevealed(context.reverseWritten(), context.completedAt(), now);
    }

    private MemberReport save(MemberReport report) {
        try {
            return memberReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw MemberReportUniqueConstraintTranslator.translate(e);
        }
    }

    private Instant suspendTarget(long reporterId, long targetMemberId, Instant now) {
        LockedMember target = memberSuspensionService.lockForSanctionAlongWith(targetMemberId, reporterId);
        Instant endsAt = now.plus(Sanction.TEMPORARY_SUSPENSION);
        memberSuspensionService.suspendTemporarily(target, endsAt);
        return endsAt;
    }
}
