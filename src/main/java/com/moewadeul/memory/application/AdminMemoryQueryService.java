package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.infra.AdminMemoryMapper;
import com.moewadeul.memory.infra.AdminMemoryRecentReportRow;
import com.moewadeul.memory.infra.AdminMemoryRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 검토 대기 또는 숨긴 공개 기록의 목록을 읽는다. 신고 내용이 들어 있으므로 호출하는 쪽이 관리자 권한을 확인해야 한다. */
@Service
@RequiredArgsConstructor
public class AdminMemoryQueryService {

    private final AdminMemoryMapper adminMemoryMapper;
    private final MemoryImageReader memoryImageReader;

    /**
     * 호출하면 status인 공개 기록을 금전 요구·사기 신고가 있는 기록부터, 같은 묶음에서는 상태가 바뀐 시각이 이른 순서로 한 페이지 돌려준다. status가
     * null이면 검토 대기(PENDING_REVIEW)를 읽는다. 검토 대기와 숨김(HIDDEN) 말고 다른 값이면 INVALID_INPUT으로 거부한다. 비공개 기록은 관리자도 보지
     * 못하므로 읽지 않는다.
     */
    @Transactional(readOnly = true)
    public AdminMemoryPage list(String status, int page, int size) {
        MemoryStatus memoryStatus = parseStatus(status);
        // 다음 쪽이 있는지 알려고 한 건 더 읽는다.
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
        if (memoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
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
