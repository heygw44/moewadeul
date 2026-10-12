package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.domain.MemoryVisibility;
import com.moewadeul.memory.infra.MemoryFeedRow;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.memory.infra.MemorySummaryRow;
import java.util.List;
import java.util.Map;
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
        return memoryMapper.selectByMember(memberId, null).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 호출하면 공개 기록을 새로 쓴 것부터 page번째 쪽(0부터)에 size건씩 돌려준다. 정확한 좌표는 작성자 본인의 기록이어도 주지 않고 대략 좌표만 준다.
     * viewerId는 좋아요를 눌렀는지 채울 회원이고, 비회원이면 null이다. 전체 개수는 세지 않고 다음 쪽이 있는지만 알린다.
     */
    @Transactional(readOnly = true)
    public MemoryFeedPage listFeed(int page, int size, Long viewerId) {
        // 다음 쪽이 있는지 알려고 한 건 더 읽는다.
        List<MemoryFeedRow> rows = memoryMapper.selectPublicFeed(viewerId, size + 1, page * size);
        boolean hasNext = rows.size() > size;
        List<MemoryFeedRow> pageRows = hasNext ? rows.subList(0, size) : rows;
        Map<Long, List<MemoryPhoto>> images = memoryImageReader.readByMemoryIds(
                pageRows.stream().map(MemoryFeedRow::memoryId).toList());
        List<MemoryFeedItem> content = pageRows.stream()
                .map(row -> toFeedItem(row, images.getOrDefault(row.memoryId(), List.of())))
                .toList();
        return new MemoryFeedPage(content, page, size, hasNext);
    }

    /**
     * 호출하면 memberId인 회원의 공개 기록을 방문일이 늦은 순서로 모두 돌려준다. 조회하는 사람이 그 회원 본인이어도 공개 기록만, 대략 좌표만 준다.
     * 회원이 없거나 탈퇴했으면 NOT_FOUND다.
     */
    @Transactional(readOnly = true)
    public List<PublicMemorySummary> listPublicOf(long memberId) {
        if (!memoryMapper.existsActiveMember(memberId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return memoryMapper.selectByMember(memberId, MemoryVisibility.PUBLIC.name()).stream()
                .map(this::toPublicSummary)
                .toList();
    }

    private MemoryFeedItem toFeedItem(MemoryFeedRow row, List<MemoryPhoto> images) {
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
                images,
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
                thumbnailUrl,
                row.imageCount(),
                row.likeCount(),
                row.createdAt());
    }
}
