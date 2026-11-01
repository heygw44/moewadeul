package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.memory.infra.MemoryRow;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MemoryViewReader {

    private final MemoryMapper memoryMapper;
    private final MemoryImageReader memoryImageReader;

    Optional<MemoryView> read(long memoryId, Long viewerId) {
        return memoryMapper.selectById(memoryId, viewerId).map(row -> toView(row, viewerId));
    }

    private MemoryView toView(MemoryRow row, Long viewerId) {
        boolean author = viewerId != null && viewerId == row.authorId();
        return new MemoryView(
                row.memoryId(),
                row.authorId(),
                row.authorNickname(),
                row.spotId(),
                row.spotName(),
                author ? row.latitude() : null,
                author ? row.longitude() : null,
                ApproximateLocation.of(row.latitude(), row.longitude()),
                row.visitedDate(),
                row.nights(),
                row.content(),
                row.visibility(),
                row.status(),
                memoryImageReader.readByMemoryId(row.memoryId()),
                row.createdAt(),
                row.updatedAt(),
                row.likeCount(),
                row.likedByMe(),
                author);
    }
}
