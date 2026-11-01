package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.moderation.ModerationStatus;
import com.moewadeul.common.moderation.RecentReport;
import com.moewadeul.common.moderation.RecentReportRow;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.bakji.BakjiReportReason;
import com.moewadeul.spot.infra.moderation.AdminSpotMapper;
import com.moewadeul.spot.infra.moderation.AdminSpotRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSpotQueryService {

    private final AdminSpotMapper adminSpotMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminSpotSummary> list(String status, int page, int size) {
        SpotStatus spotStatus = ModerationStatus.parseReviewable(SpotStatus.class, status);
        List<AdminSpotRow> rows =
                adminSpotMapper.selectPage(spotStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminSpotRow> slice = PageSlice.of(rows, page, size);
        List<Long> spotIds = slice.content().stream().map(AdminSpotRow::spotId).toList();
        Map<Long, List<RecentReport>> recentBySpot = readRecentReports(spotIds);
        Map<Long, Map<String, Long>> reasonCounts = readReasonCounts(spotIds);
        return slice.map(row ->
                toSummary(row, reasonCounts.get(row.spotId()), recentBySpot.getOrDefault(row.spotId(), List.of())));
    }

    private Map<Long, Map<String, Long>> readReasonCounts(List<Long> spotIds) {
        List<ReportReasonCount> counts = spotIds.isEmpty() ? List.of() : adminSpotMapper.selectReasonCounts(spotIds);
        return ReportReasonCount.byTarget(BakjiReportReason.class, spotIds, counts);
    }

    private Map<Long, List<RecentReport>> readRecentReports(List<Long> spotIds) {
        if (spotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return RecentReportRow.byTarget(adminSpotMapper.selectRecentReports(spotIds));
    }

    private static AdminSpotSummary toSummary(
            AdminSpotRow row, Map<String, Long> reasonCounts, List<RecentReport> recentReports) {
        AdminSpotSummary.Reporter reporter = row.reporterId() == null
                ? null
                : new AdminSpotSummary.Reporter(row.reporterId(), row.reporterNickname());
        return new AdminSpotSummary(
                row.spotId(),
                row.type().name(),
                row.name(),
                row.status().name(),
                row.lat(),
                row.lng(),
                row.parkWarning(),
                reporter,
                row.reportCount(),
                reasonCounts,
                recentReports,
                row.statusChangedAt());
    }
}
