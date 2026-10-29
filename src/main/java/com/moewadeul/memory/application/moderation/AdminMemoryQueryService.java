package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.memory.application.MemoryImageReader;
import com.moewadeul.memory.application.MemoryPhoto;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.infra.moderation.AdminMemoryMapper;
import com.moewadeul.memory.infra.moderation.AdminMemoryRecentReportRow;
import com.moewadeul.memory.infra.moderation.AdminMemoryRow;
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
public class AdminMemoryQueryService {

    private static final Set<MemoryStatus> REVIEWABLE_STATUSES =
            EnumSet.of(MemoryStatus.PENDING_REVIEW, MemoryStatus.HIDDEN);
    private static final String STATUS_MESSAGE = "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.";

    private final AdminMemoryMapper adminMemoryMapper;
    private final MemoryImageReader memoryImageReader;

    @Transactional(readOnly = true)
    public PageSlice<AdminMemorySummary> list(String status, int page, int size) {
        MemoryStatus memoryStatus = parseStatus(status);
        List<AdminMemoryRow> rows = adminMemoryMapper.selectMemories(
                memoryStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminMemoryRow> slice = PageSlice.of(rows, page, size);
        List<Long> memoryIds =
                slice.content().stream().map(AdminMemoryRow::memoryId).toList();
        Map<Long, List<AdminMemorySummary.RecentReport>> recent = readRecentReports(memoryIds);
        Map<Long, Map<String, Long>> reasonCounts = ReportReasonCount.byTarget(
                ContentReportReason.class, memoryIds, adminMemoryMapper.selectReasonCounts(memoryIds));
        Map<Long, List<MemoryPhoto>> images = memoryImageReader.readByMemoryIds(memoryIds);
        return slice.map(row -> toSummary(row, images, reasonCounts.get(row.memoryId()), recent));
    }

    private static AdminMemorySummary toSummary(
            AdminMemoryRow row,
            Map<Long, List<MemoryPhoto>> images,
            Map<String, Long> reasonCounts,
            Map<Long, List<AdminMemorySummary.RecentReport>> recent) {
        ApproximateLocation approximate = ApproximateLocation.of(row.latitude(), row.longitude());
        return new AdminMemorySummary(
                row.memoryId(),
                row.authorId(),
                row.authorNickname(),
                approximate.latitude(),
                approximate.longitude(),
                row.visitedDate(),
                row.nights(),
                row.content(),
                images.getOrDefault(row.memoryId(), List.of()),
                row.status().name(),
                row.reportCount(),
                reasonCounts,
                recent.getOrDefault(row.memoryId(), List.of()),
                row.statusChangedAt());
    }

    private Map<Long, List<AdminMemorySummary.RecentReport>> readRecentReports(List<Long> memoryIds) {
        return adminMemoryMapper.selectRecentReports(memoryIds).stream()
                .collect(Collectors.groupingBy(
                        AdminMemoryRecentReportRow::memoryId,
                        Collectors.mapping(
                                row -> new AdminMemorySummary.RecentReport(
                                        row.reason().name(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static MemoryStatus parseStatus(String status) {
        if (status == null) {
            return MemoryStatus.PENDING_REVIEW;
        }
        return EnumParser.parseAmong(REVIEWABLE_STATUSES, status, STATUS_MESSAGE);
    }
}
