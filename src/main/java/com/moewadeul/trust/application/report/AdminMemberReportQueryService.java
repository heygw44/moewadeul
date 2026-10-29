package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.paging.PageSlice;
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

    private static final String STATUS_MESSAGE = "알 수 없는 신고 상태예요.";

    private final AdminMemberReportMapper adminMemberReportMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminMemberReportSummary> list(String status, Boolean urgent, int page, int size) {
        ReportStatus reportStatus = EnumParser.parseNullable(ReportStatus.class, status, STATUS_MESSAGE);
        List<AdminMemberReportRow> rows = adminMemberReportMapper.selectList(
                reportStatus, urgent, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        return PageSlice.of(rows, page, size).map(AdminMemberReportQueryService::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminMemberReportDetail detail(long reportId) {
        AdminMemberReportDetailRow row =
                adminMemberReportMapper.selectDetail(reportId).orElseThrow(BusinessException::notFound);
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
                row.kind().name(),
                row.type().name(),
                row.urgent(),
                row.status().name(),
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
                row.kind().name(),
                row.type().name(),
                row.urgent(),
                row.status().name(),
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
                row.type().name(),
                row.level(),
                row.status().name(),
                row.reason(),
                row.startsAt(),
                row.endsAt(),
                row.liftedAt());
    }
}
