package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.bakji.BakjiReportReason;
import com.moewadeul.spot.infra.moderation.AdminSpotMapper;
import com.moewadeul.spot.infra.moderation.AdminSpotRecentReportRow;
import com.moewadeul.spot.infra.moderation.AdminSpotRow;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSpotQueryService {

    private static final Set<SpotStatus> REVIEWABLE_STATUSES = EnumSet.of(SpotStatus.PENDING_REVIEW, SpotStatus.HIDDEN);
    private static final String STATUS_MESSAGE = "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.";

    private final AdminSpotMapper adminSpotMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminSpotSummary> list(String status, int page, int size) {
        SpotStatus spotStatus = parseStatus(status);
        List<AdminSpotRow> rows =
                adminSpotMapper.selectPage(spotStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminSpotRow> slice = PageSlice.of(rows, page, size);
        List<Long> spotIds = slice.content().stream().map(AdminSpotRow::spotId).toList();
        Map<Long, List<AdminSpotSummary.RecentReport>> recentBySpot = readRecentReports(spotIds);
        Map<Long, Map<String, Long>> reasonCounts = readReasonCounts(spotIds);
        return slice.map(row ->
                toSummary(row, reasonCounts.get(row.spotId()), recentBySpot.getOrDefault(row.spotId(), List.of())));
    }

    private Map<Long, Map<String, Long>> readReasonCounts(List<Long> spotIds) {
        List<ReportReasonCount> counts = spotIds.isEmpty() ? List.of() : adminSpotMapper.selectReasonCounts(spotIds);
        return ReportReasonCount.byTarget(BakjiReportReason.class, spotIds, counts);
    }

    private Map<Long, List<AdminSpotSummary.RecentReport>> readRecentReports(List<Long> spotIds) {
        if (spotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return adminSpotMapper.selectRecentReports(spotIds).stream()
                .collect(Collectors.groupingBy(
                        AdminSpotRecentReportRow::spotId,
                        Collectors.mapping(
                                row -> new AdminSpotSummary.RecentReport(
                                        row.reason().name(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static SpotStatus parseStatus(String status) {
        if (status == null) {
            return SpotStatus.PENDING_REVIEW;
        }
        return EnumParser.parseAmong(REVIEWABLE_STATUSES, status, STATUS_MESSAGE);
    }

    private static AdminSpotSummary toSummary(
            AdminSpotRow row, Map<String, Long> reasonCounts, List<AdminSpotSummary.RecentReport> recentReports) {
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
