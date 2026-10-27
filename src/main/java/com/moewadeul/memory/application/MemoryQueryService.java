package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.MemoryVisibility;
import com.moewadeul.memory.infra.MemoryFeedRow;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.memory.infra.MemorySummaryRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryQueryService {

    private final MemoryViewReader memoryViewReader;
    private final MemoryMapper memoryMapper;
    private final MemoryImageReader memoryImageReader;

    @Transactional(readOnly = true)
    public MemoryView findVisible(long memoryId, Long viewerId) {
        return memoryViewReader
                .read(memoryId, viewerId)
                .filter(view -> view.viewedByAuthor()
                        || (MemoryVisibility.PUBLIC.name().equals(view.visibility())
                                && MemoryStatus.ACTIVE.name().equals(view.status())))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<MemorySummary> listMine(long memberId) {
        return memoryMapper.selectByMember(memberId, null).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemoryFeedPage listFeed(int page, int size, Long viewerId) {
        List<MemoryFeedRow> rows = memoryMapper.selectPublicFeed(viewerId, size + 1, (long) page * size);
        boolean hasNext = rows.size() > size;
        List<MemoryFeedRow> pageRows = hasNext ? rows.subList(0, size) : rows;
        List<MemoryFeedItem> content = pageRows.stream().map(this::toFeedItem).toList();
        return new MemoryFeedPage(content, page, size, hasNext);
    }

    @Transactional(readOnly = true)
    public List<PublicMemorySummary> listPublicOf(long memberId) {
        if (!memoryMapper.existsActiveMember(memberId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return memoryMapper.selectByMember(memberId, MemoryVisibility.PUBLIC.name()).stream()
                .map(this::toPublicSummary)
                .toList();
    }

    private MemoryFeedItem toFeedItem(MemoryFeedRow row) {
        String thumbnailUrl = row.thumbnailKey() == null ? null : memoryImageReader.viewUrlOf(row.thumbnailKey());
        return new MemoryFeedItem(
                row.memoryId(),
                row.authorId(),
                row.authorNickname(),
                row.spotId(),
                row.spotName(),
                ApproximateLocation.of(row.latitude(), row.longitude()),
                row.visitedDate(),
                row.nights(),
                row.excerpt(),
                thumbnailUrl,
                row.imageCount(),
                row.likeCount(),
                row.likedByMe(),
                row.createdAt());
    }

    private PublicMemorySummary toPublicSummary(MemorySummaryRow row) {
        String thumbnailUrl = row.thumbnailKey() == null ? null : memoryImageReader.viewUrlOf(row.thumbnailKey());
        return new PublicMemorySummary(
                row.memoryId(),
                row.spotId(),
                row.spotName(),
                ApproximateLocation.of(row.latitude(), row.longitude()),
                row.visitedDate(),
                row.nights(),
                row.excerpt(),
                thumbnailUrl,
                row.imageCount(),
                row.likeCount(),
                row.createdAt());
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
                row.status(),
                thumbnailUrl,
                row.imageCount(),
                row.likeCount(),
                row.createdAt());
    }
}
