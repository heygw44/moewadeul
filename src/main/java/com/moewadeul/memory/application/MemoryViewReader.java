package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.memory.infra.MemoryRow;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 기록 한 건과 붙은 사진을 읽어 {@link MemoryView}로 만든다. 상세 조회와 수정 응답이 함께 쓰고, 호출하는 쪽의 트랜잭션 안에서 부른다. */
@Component
@RequiredArgsConstructor
class MemoryViewReader {

    private final MemoryMapper memoryMapper;
    private final MemoryImageReader memoryImageReader;

    /** 호출하면 memoryId인 기록을 viewerId인 사람(비회원이면 null)이 보는 모양으로 읽는다. 공개 범위는 거르지 않는다. 기록이 없으면 빈 값이다. */
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
