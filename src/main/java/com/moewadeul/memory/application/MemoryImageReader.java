package com.moewadeul.memory.application;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.infra.MemoryImageMapper;
import com.moewadeul.memory.infra.MemoryImageRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기록에 붙은 사진을 읽고 조회 URL을 붙인다. 호출하는 쪽의 트랜잭션 안에서 부른다. 조회 URL은 서버 안에서 서명을 계산하므로 저장소를 부르지 않는다.
 */
@Component
@RequiredArgsConstructor
class MemoryImageReader {

    private final MemoryImageMapper memoryImageMapper;
    private final ImageStorage imageStorage;

    /** 호출하면 memoryId인 기록에 붙은 사진을 기록 안 순서대로 돌려준다. 사진이 없으면 빈 목록이다. */
    List<MemoryPhoto> readByMemoryId(long memoryId) {
        return memoryImageMapper.selectByMemoryIds(List.of(memoryId)).stream()
                .map(this::toPhoto)
                .toList();
    }

    /** 호출하면 objectKey인 사진의 조회 URL을 서명한다. */
    String viewUrlOf(String objectKey) {
        return imageStorage.presignView(objectKey);
    }

    private MemoryPhoto toPhoto(MemoryImageRow row) {
        return new MemoryPhoto(row.imageId(), imageStorage.presignView(row.objectKey()));
    }
}
