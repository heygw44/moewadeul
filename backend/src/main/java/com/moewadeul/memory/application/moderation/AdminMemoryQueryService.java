package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.moderation.ModerationStatus;
import com.moewadeul.common.moderation.RecentReport;
import com.moewadeul.common.moderation.RecentReportRow;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.memory.application.MemoryImageReader;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.infra.moderation.AdminMemoryMapper;
import com.moewadeul.memory.infra.moderation.AdminMemoryRow;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemoryQueryService {

    private final AdminMemoryMapper adminMemoryMapper;
    private final MemoryImageReader memoryImageReader;

    @Transactional(readOnly = true)
    public PageSlice<AdminMemorySummary> list(String status, int page, int size) {
        MemoryStatus memoryStatus = ModerationStatus.parseReviewable(MemoryStatus.class, status);
        List<AdminMemoryRow> rows = adminMemoryMapper.selectMemories(
                memoryStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminMemoryRow> slice = PageSlice.of(rows, page, size);
        List<Long> memoryIds =
                slice.content().stream().map(AdminMemoryRow::memoryId).toList();
        Map<Long, List<RecentReport>> recent =
                RecentReportRow.byTarget(adminMemoryMapper.selectRecentReports(memoryIds));
        Map<Long, Map<String, Long>> reasonCounts = ReportReasonCount.byTarget(
                ContentReportReason.class, memoryIds, adminMemoryMapper.selectReasonCounts(memoryIds));
        Map<Long, List<ImageView>> images = memoryImageReader.readByMemoryIds(memoryIds);
        return slice.map(row -> toSummary(row, images, reasonCounts.get(row.memoryId()), recent));
    }

    private static AdminMemorySummary toSummary(
            AdminMemoryRow row,
            Map<Long, List<ImageView>> images,
            Map<String, Long> reasonCounts,
            Map<Long, List<RecentReport>> recent) {
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
}
