package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.trust.domain.report.ReportStatus;
import com.moewadeul.trust.infra.report.AdminMemberReportDetailRow;
import com.moewadeul.trust.infra.report.AdminMemberReportMapper;
import com.moewadeul.trust.infra.report.AdminMemberReportRow;
import com.moewadeul.trust.infra.report.AdminSanctionHistoryRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemberReportQueryService {

    private final AdminMemberReportMapper adminMemberReportMapper;

    @Transactional(readOnly = true)
    public AdminMemberReportPage list(String status, Boolean urgent, int page, int size) {
        String statusName = status == null ? null : parseStatus(status).name();
        long offset = (long) page * size;
        List<AdminMemberReportRow> rows = adminMemberReportMapper.selectList(statusName, urgent, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminMemberReportSummary> content = rows.stream()
                .limit(size)
                .map(AdminMemberReportQueryService::toSummary)
                .toList();
        return new AdminMemberReportPage(content, page, size, hasNext);
    }

    @Transactional(readOnly = true)
    public AdminMemberReportDetail detail(long reportId) {
        AdminMemberReportDetailRow row = adminMemberReportMapper
                .selectDetail(reportId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<AdminMemberReportDetail.SanctionHistory> history =
                adminMemberReportMapper.selectSanctionHistory(row.targetId()).stream()
                        .map(AdminMemberReportQueryService::toHistory)
                        .toList();
        return new AdminMemberReportDetail(
                toSummary(row),
                row.content(),
                row.resultNote(),
                row.handledBy(),
                row.handledAt(),
                new AdminMemberReportDetail.Basecamp(
                        row.basecampId(), row.basecampTitle(), row.basecampStatus(), row.basecampStartDate()),
                toReview(row),
                history);
    }

    private static ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "알 수 없는 신고 상태예요.");
        }
    }

    private AdminMemberReportDetail.Review toReview(AdminMemberReportDetailRow row) {
        if (row.reviewId() == null) {
            return null;
        }
        return new AdminMemberReportDetail.Review(
                row.reviewComment(),
                adminMemberReportMapper.selectReviewTags(row.reviewId()),
                Boolean.TRUE.equals(row.reviewRejoinWanted()),
                Boolean.TRUE.equals(row.reviewHidden()));
    }

    private static AdminMemberReportSummary toSummary(AdminMemberReportRow row) {
        return new AdminMemberReportSummary(
                row.reportId(),
                row.kind(),
                row.type(),
                row.urgent(),
                row.status(),
                row.reporterId(),
                row.reporterNickname(),
                row.targetId(),
                row.targetNickname(),
                row.basecampId(),
                row.createdAt());
    }

    private static AdminMemberReportSummary toSummary(AdminMemberReportDetailRow row) {
        return new AdminMemberReportSummary(
                row.reportId(),
                row.kind(),
                row.type(),
                row.urgent(),
                row.status(),
                row.reporterId(),
                row.reporterNickname(),
                row.targetId(),
                row.targetNickname(),
                row.basecampId(),
                row.createdAt());
    }

    private static AdminMemberReportDetail.SanctionHistory toHistory(AdminSanctionHistoryRow row) {
        return new AdminMemberReportDetail.SanctionHistory(
                row.sanctionId(),
                row.type(),
                row.level(),
                row.status(),
                row.reason(),
                row.startsAt(),
                row.endsAt(),
                row.liftedAt());
    }
}
