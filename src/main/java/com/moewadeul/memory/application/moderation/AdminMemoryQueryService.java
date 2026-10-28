package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.application.MemoryImageReader;
import com.moewadeul.memory.application.MemoryPhoto;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.infra.moderation.AdminMemoryMapper;
import com.moewadeul.memory.infra.moderation.AdminMemoryRecentReportRow;
import com.moewadeul.memory.infra.moderation.AdminMemoryRow;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemoryQueryService {

    private final AdminMemoryMapper adminMemoryMapper;
    private final MemoryImageReader memoryImageReader;

    @Transactional(readOnly = true)
    public AdminMemoryPage list(String status, int page, int size) {
        MemoryStatus memoryStatus = parseStatus(status);
        List<AdminMemoryRow> rows = adminMemoryMapper.selectMemories(memoryStatus.name(), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminMemoryRow> pageRows = rows.stream().limit(size).toList();
        List<Long> memoryIds = pageRows.stream().map(AdminMemoryRow::memoryId).toList();
        Map<Long, List<AdminMemorySummary.RecentReport>> recent = readRecentReports(memoryIds);
        Map<Long, List<MemoryPhoto>> images = memoryImageReader.readByMemoryIds(memoryIds);
        List<AdminMemorySummary> content =
                pageRows.stream().map(row -> toSummary(row, images, recent)).toList();
        return new AdminMemoryPage(content, page, size, hasNext);
    }

    private static AdminMemorySummary toSummary(
            AdminMemoryRow row,
            Map<Long, List<MemoryPhoto>> images,
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
                row.status(),
                row.reportCount(),
                new AdminMemorySummary.ReasonCounts(
                        row.spamCount(),
                        row.abuseCount(),
                        row.illegalCampingCount(),
                        row.privacyCount(),
                        row.moneyScamCount(),
                        row.otherCount()),
                recent.getOrDefault(row.memoryId(), List.of()),
                row.statusChangedAt());
    }

    private Map<Long, List<AdminMemorySummary.RecentReport>> readRecentReports(List<Long> memoryIds) {
        return adminMemoryMapper.selectRecentReports(memoryIds).stream()
                .collect(Collectors.groupingBy(
                        AdminMemoryRecentReportRow::memoryId,
                        Collectors.mapping(
                                row -> new AdminMemorySummary.RecentReport(
                                        row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static MemoryStatus parseStatus(String status) {
        if (status == null) {
            return MemoryStatus.PENDING_REVIEW;
        }
        for (MemoryStatus candidate : List.of(MemoryStatus.PENDING_REVIEW, MemoryStatus.HIDDEN)) {
            if (candidate.name().equals(status)) {
                return candidate;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.");
    }
}
