package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.infra.moderation.AdminSpotMapper;
import com.moewadeul.spot.infra.moderation.AdminSpotRecentReportRow;
import com.moewadeul.spot.infra.moderation.AdminSpotRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSpotQueryService {

    private final AdminSpotMapper adminSpotMapper;

    @Transactional(readOnly = true)
    public AdminSpotPage list(String status, int page, int size) {
        SpotStatus spotStatus = parseStatus(status);
        long offset = (long) page * size;
        List<AdminSpotRow> rows = adminSpotMapper.selectPage(spotStatus.name(), offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminSpotRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminSpotSummary.RecentReport>> recentBySpot = readRecentReports(pageRows);
        List<AdminSpotSummary> content = pageRows.stream()
                .map(row -> toSummary(row, recentBySpot.getOrDefault(row.spotId(), List.of())))
                .toList();
        return new AdminSpotPage(content, page, size, hasNext);
    }

    private Map<Long, List<AdminSpotSummary.RecentReport>> readRecentReports(List<AdminSpotRow> pageRows) {
        if (pageRows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> spotIds = pageRows.stream().map(AdminSpotRow::spotId).toList();
        return adminSpotMapper.selectRecentReports(spotIds).stream()
                .collect(Collectors.groupingBy(
                        AdminSpotRecentReportRow::spotId,
                        Collectors.mapping(
                                row -> new AdminSpotSummary.RecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static SpotStatus parseStatus(String status) {
        if (status == null) {
            return SpotStatus.PENDING_REVIEW;
        }
        for (SpotStatus candidate : List.of(SpotStatus.PENDING_REVIEW, SpotStatus.HIDDEN)) {
            if (candidate.name().equals(status)) {
                return candidate;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.");
    }

    private static AdminSpotSummary toSummary(AdminSpotRow row, List<AdminSpotSummary.RecentReport> recentReports) {
        AdminSpotSummary.Reporter reporter = row.reporterId() == null
                ? null
                : new AdminSpotSummary.Reporter(row.reporterId(), row.reporterNickname());
        return new AdminSpotSummary(
                row.spotId(),
                row.type(),
                row.name(),
                row.status(),
                row.lat(),
                row.lng(),
                row.parkWarning(),
                reporter,
                row.reportCount(),
                new AdminSpotSummary.ReasonCounts(row.illegalAreaCount(), row.closedCount(), row.falseInfoCount()),
                recentReports,
                row.statusChangedAt());
    }
}
