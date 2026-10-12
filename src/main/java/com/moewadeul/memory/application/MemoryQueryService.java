package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.MemoryVisibility;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.memory.infra.MemorySummaryRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추억 기록을 읽는다. 비공개 기록은 작성자만 보고, 다른 사람(비회원 포함)에게는 없는 기록과 같은 NOT_FOUND로 응답한다. 그래야 비공개 기록이 있다는
 * 사실도 드러나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemoryQueryService {

    private final MemoryViewReader memoryViewReader;
    private final MemoryMapper memoryMapper;
    private final MemoryImageReader memoryImageReader;

    /** 호출하면 viewerId인 사람(비회원이면 null)이 볼 수 있는 memoryId인 기록을 돌려준다. 기록이 없거나 남의 비공개 기록이면 NOT_FOUND다. */
    @Transactional(readOnly = true)
    public MemoryView findVisible(long memoryId, Long viewerId) {
        return memoryViewReader
                .read(memoryId, viewerId)
                .filter(view ->
                        view.viewedByAuthor() || MemoryVisibility.PUBLIC.name().equals(view.visibility()))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 호출하면 memberId인 회원의 기록을 공개 범위와 상관없이 모두 방문일이 늦은 순서로 돌려준다. */
    @Transactional(readOnly = true)
    public List<MemorySummary> listMine(long memberId) {
        return memoryMapper.selectByMember(memberId).stream()
                .map(this::toSummary)
                .toList();
    }

    private MemorySummary toSummary(MemorySummaryRow row) {
        String thumbnailUrl = row.thumbnailKey() == null ? null : memoryImageReader.viewUrlOf(row.thumbnailKey());
        return new MemorySummary(
                row.memoryId(),
                row.spotId(),
                row.spotName(),
                row.latitude(),
                row.longitude(),
                row.visitedDate(),
                row.nights(),
                row.excerpt(),
                row.visibility(),
                thumbnailUrl,
                row.imageCount(),
                row.createdAt());
    }
}
